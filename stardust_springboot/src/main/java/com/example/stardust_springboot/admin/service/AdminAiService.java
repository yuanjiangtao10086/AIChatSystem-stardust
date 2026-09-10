package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.ai.catalog.*;
import com.example.stardust_springboot.ai.entity.*;
import com.example.stardust_springboot.ai.repository.*;
import com.example.stardust_springboot.ai.request.*;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrator management of the AI catalog: providers and models.
 *
 * <p>Secrets are never stored, returned or audited here. A provider keeps a credential
 * <em>reference</em> (ADR-016); reads only expose {@code hasApiKey} plus a mask of the value the
 * deployment actually provides, and every write is audited with non-secret metadata only.
 *
 * <p>Runtime provider connection facts (base URL, key, timeout, default model) still come from the
 * AI service settings (ADR-032): this service owns the catalog and the enable/default switches, not
 * the wire configuration.
 */
@Service
public class AdminAiService {

    private final AiProviderRepository providers;
    private final AiModelRepository models;
    private final AiRequestLogRepository requests;
    private final AdminAuditService audit;
    private final AiCatalogJsonCodec codec;
    private final ProviderCredentialResolver credentials;

    public AdminAiService(AiProviderRepository providers, AiModelRepository models,
                          AiRequestLogRepository requests, AdminAuditService audit,
                          AiCatalogJsonCodec codec, ProviderCredentialResolver credentials) {
        this.providers = providers;
        this.models = models;
        this.requests = requests;
        this.audit = audit;
        this.codec = codec;
        this.credentials = credentials;
    }

    // ---------------------------------------------------------------- providers

    @Transactional(readOnly = true)
    public List<AdminDtos.ProviderView> providers() {
        return providers.findAll(Sort.by("createdAt")).stream().map(this::providerView).toList();
    }

    @Transactional
    public AdminDtos.ProviderView createProvider(AuthenticatedUser actor, AdminDtos.ProviderRequest r) {
        if (providers.findByCode(r.code()).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        String reference = normalizeRef(r.credentialRef());
        AiProvider provider = providers.save(new AiProvider(r.code(), r.displayName(), r.type(), r.baseUrl(), reference));
        provider.update(r.displayName(), r.type(), r.baseUrl(), reference, configJson(r));
        audit.record(actor, AdminAuditAction.PROVIDER_CREATE, null, "AI_PROVIDER", provider.getPublicId(),
                codec.writeMetadata(metadata("type", r.type().name(), "credentialConfigured", reference != null,
                        "timeoutSeconds", r.timeoutSeconds())));
        return providerView(provider);
    }

    @Transactional
    public AdminDtos.ProviderView updateProvider(AuthenticatedUser actor, String id, AdminDtos.ProviderRequest r) {
        AiProvider provider = requireProvider(id);
        if (!provider.getCode().equals(r.code())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        // A blank reference keeps the stored one; only an explicit new reference replaces it.
        String reference = normalizeRef(r.credentialRef());
        provider.update(r.displayName(), r.type(), r.baseUrl(), reference, configJson(r));
        audit.record(actor, AdminAuditAction.PROVIDER_UPDATE, null, "AI_PROVIDER", id,
                codec.writeMetadata(metadata("type", r.type().name(), "credentialReplaced", reference != null,
                        "timeoutSeconds", r.timeoutSeconds())));
        return providerView(provider);
    }

    @Transactional
    public AdminDtos.ProviderView providerStatus(AuthenticatedUser actor, String id, boolean enabled) {
        AiProvider provider = requireProvider(id);
        if (enabled) {
            provider.enable();
        } else {
            provider.disable();
        }
        audit.record(actor, AdminAuditAction.PROVIDER_STATUS_UPDATE, null, "AI_PROVIDER", id,
                codec.writeMetadata(metadata("enabled", enabled)));
        return providerView(provider);
    }

    /**
     * Hard-deletes a provider. Allowed only while nothing depends on it: models must be removed
     * first and request logs reference providers with {@code ON DELETE RESTRICT}, so a provider that
     * ever served traffic can only be disabled.
     */
    @Transactional
    public void deleteProvider(AuthenticatedUser actor, String id) {
        AiProvider provider = requireProvider(id);
        long ownedModels = models.countByProviderId(provider.getId());
        if (ownedModels > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (requests.countByProviderId(provider.getId()) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        providers.delete(provider);
        audit.record(actor, AdminAuditAction.PROVIDER_DELETE, null, "AI_PROVIDER", id,
                codec.writeMetadata(metadata("code", provider.getCode())));
    }

    // ---------------------------------------------------------------- models

    @Transactional(readOnly = true)
    public List<AdminDtos.ModelView> models() {
        return models.findCatalog().stream().map(this::modelView).toList();
    }

    @Transactional
    public AdminDtos.ModelView createModel(AuthenticatedUser actor, AdminDtos.ModelRequest r) {
        AiProvider provider = requireProvider(r.providerId());
        if (models.findByCode(r.code()).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (models.findByProviderIdAndExternalModelId(provider.getId(), r.externalModelId()).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AiModel model = models.save(new AiModel(provider, r.code(), r.externalModelId(), r.displayName(), r.type()));
        apply(model, r);
        audit.record(actor, AdminAuditAction.MODEL_CREATE, null, "AI_MODEL", model.getPublicId(),
                codec.writeMetadata(metadata("providerId", provider.getPublicId(), "code", r.code(), "type", r.type().name())));
        return modelView(model);
    }

    @Transactional
    public AdminDtos.ModelView updateModel(AuthenticatedUser actor, String id, AdminDtos.ModelRequest r) {
        AiModel model = requireModel(id);
        if (!model.getCode().equals(r.code()) || !model.getProvider().getPublicId().equals(r.providerId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        apply(model, r);
        audit.record(actor, AdminAuditAction.MODEL_UPDATE, null, "AI_MODEL", id,
                codec.writeMetadata(metadata("type", r.type().name(), "defaultModel", model.isDefault())));
        return modelView(model);
    }

    /** Disabling a model also clears its default flag: a disabled model cannot be offered as default. */
    @Transactional
    public AdminDtos.ModelView modelStatus(AuthenticatedUser actor, String id, boolean enabled) {
        AiModel model = requireModel(id);
        if (enabled) {
            model.enable();
        } else {
            model.disable();
            model.clearDefault();
        }
        audit.record(actor, AdminAuditAction.MODEL_STATUS_UPDATE, null, "AI_MODEL", id,
                codec.writeMetadata(metadata("enabled", enabled)));
        return modelView(model);
    }

    /** Marks one model as the platform default inside its own model type, clearing the previous one. */
    @Transactional
    public AdminDtos.ModelView modelDefault(AuthenticatedUser actor, String id, boolean defaultModel) {
        AiModel model = requireModel(id);
        if (defaultModel) {
            if (model.getStatus() != ModelStatus.ENABLED) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            models.findByModelTypeAndIsDefaultTrue(model.getModelType()).stream()
                    .filter(other -> !other.getPublicId().equals(id))
                    .forEach(AiModel::clearDefault);
            model.markDefault();
        } else {
            model.clearDefault();
        }
        audit.record(actor, AdminAuditAction.MODEL_DEFAULT_UPDATE, null, "AI_MODEL", id,
                codec.writeMetadata(metadata("defaultModel", defaultModel, "type", model.getModelType().name())));
        return modelView(model);
    }

    /**
     * Moves a model one slot up or down among its provider's models.
     *
     * <p>Positions are renumbered to 10/20/30... first, so two models sharing a sort order (or a
     * hand-edited value) still move deterministically.
     */
    @Transactional
    public List<AdminDtos.ModelView> moveModel(AuthenticatedUser actor, String id, String direction) {
        AiModel model = requireModel(id);
        List<AiModel> siblings = models.findByProviderIdOrderBySortOrderAscIdAsc(model.getProvider().getId());
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).moveTo((i + 1) * 10);
        }
        int index = indexOf(siblings, id);
        int target = "UP".equals(direction) ? index - 1 : index + 1;
        if (index < 0 || target < 0 || target >= siblings.size()) {
            return models();
        }
        AiModel mine = siblings.get(index);
        AiModel neighbour = siblings.get(target);
        int mineOrder = mine.getSortOrder();
        mine.moveTo(neighbour.getSortOrder());
        neighbour.moveTo(mineOrder);
        audit.record(actor, AdminAuditAction.MODEL_REORDER, null, "AI_MODEL", id,
                codec.writeMetadata(metadata("direction", direction)));
        return models();
    }

    /** Hard-deletes a model; request logs reference models with {@code ON DELETE RESTRICT}. */
    @Transactional
    public void deleteModel(AuthenticatedUser actor, String id) {
        AiModel model = requireModel(id);
        if (requests.countByModelId(model.getId()) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        models.delete(model);
        audit.record(actor, AdminAuditAction.MODEL_DELETE, null, "AI_MODEL", id,
                codec.writeMetadata(metadata("code", model.getCode())));
    }

    // ---------------------------------------------------------------- request logs

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.AiRequestView> requestLogs(int page, int size, String userId, AiRequestStatus status,
                                                           String provider, String model, Instant from, Instant to) {
        return PageResult.from(requests.findAdmin(blank(userId), status, blank(provider), blank(model), from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(AdminDtos::of));
    }

    /** Single request log for the console detail drawer; request ids come from the list endpoint. */
    @Transactional(readOnly = true)
    public AdminDtos.AiRequestView requestLog(String requestId) {
        return AdminDtos.of(requests.findByRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    // ---------------------------------------------------------------- mapping

    private void apply(AiModel model, AdminDtos.ModelRequest r) {
        model.update(r.displayName(), r.externalModelId(), r.type(),
                codec.writeCapabilities(r.capabilities()),
                r.contextWindow(), r.maxOutputTokens(), r.inputPrice(), r.outputPrice(), r.currency(),
                codec.writeParameters(new ModelParameters(r.defaultTemperature(), r.defaultTopP(), r.defaultMaxOutputTokens())),
                r.sortOrder());
    }

    private String configJson(AdminDtos.ProviderRequest r) {
        return codec.writeProviderConfig(new ProviderConfig(r.timeoutSeconds(), r.connectTimeoutSeconds()));
    }

    /**
     * Audit metadata builder. {@code Map.of} rejects nulls, and most optional fields (timeout, sort
     * order, credential flag) legitimately arrive empty; {@code writeMetadata} drops the nulls.
     */
    private Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            values.put((String) keyValues[i], keyValues[i + 1]);
        }
        return values;
    }

    private AdminDtos.ProviderView providerView(AiProvider p) {
        ProviderConfig config = codec.readProviderConfig(p.getNonSecretConfigJson());
        // The mask is derived from the value the deployment provides at runtime, never from the database.
        String masked = credentials.resolve(p.getCredentialRef()).map(CredentialMask::of).orElse(null);
        return new AdminDtos.ProviderView(p.getPublicId(), p.getCode(), p.getDisplayName(), p.getProviderType(),
                p.getBaseUrl(), p.hasCredential(), masked, config.timeoutSeconds(), config.connectTimeoutSeconds(),
                p.getStatus(), p.getHealthStatus(), p.getLastHealthCheckedAt(),
                models.countByProviderId(p.getId()), p.getCreatedAt(), p.getUpdatedAt());
    }

    private AdminDtos.ModelView modelView(AiModel m) {
        ModelCapabilities capabilities = codec.readCapabilities(m.getCapabilitiesJson());
        ModelParameters parameters = codec.readParameters(m.getParameterPolicyJson());
        return new AdminDtos.ModelView(m.getPublicId(), m.getProvider().getPublicId(), m.getProvider().getDisplayName(),
                m.getCode(), m.getExternalModelId(), m.getDisplayName(), m.getModelType(), capabilities,
                m.getContextWindow(), m.getMaxOutputTokens(),
                parameters.defaultTemperature(), parameters.defaultTopP(), parameters.defaultMaxOutputTokens(),
                m.getInputPrice(), m.getOutputPrice(), m.getCurrency(), m.getStatus(), m.isDefault(),
                m.getSortOrder(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private AiProvider requireProvider(String id) {
        return providers.findByPublicId(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AiModel requireModel(String id) {
        return models.findByPublicId(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private int indexOf(List<AiModel> rows, String publicId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getPublicId().equals(publicId)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeRef(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
