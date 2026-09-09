package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.ai.entity.*;
import com.example.stardust_springboot.ai.repository.*;
import com.example.stardust_springboot.ai.request.*;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAiService {
    private final AiProviderRepository providers; private final AiModelRepository models;
    private final AiRequestLogRepository requests; private final AdminAuditService audit;
    public AdminAiService(AiProviderRepository providers,AiModelRepository models,AiRequestLogRepository requests,AdminAuditService audit){
        this.providers=providers;this.models=models;this.requests=requests;this.audit=audit;
    }
    @Transactional(readOnly=true) public List<AdminDtos.ProviderView> providers(){return providers.findAll(Sort.by("createdAt")).stream().map(this::providerView).toList();}
    @Transactional public AdminDtos.ProviderView createProvider(AuthenticatedUser actor,AdminDtos.ProviderRequest r){
        if(providers.findByCode(r.code()).isPresent()) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        AiProvider p=providers.save(new AiProvider(r.code(),r.displayName(),r.type(),r.baseUrl(),r.credentialRef()));
        p.update(r.displayName(),r.type(),r.baseUrl(),r.credentialRef(),r.configJson());
        audit.record(actor,AdminAuditAction.PROVIDER_CREATE,null,"AI_PROVIDER",p.getPublicId(),null);return providerView(p);
    }
    @Transactional public AdminDtos.ProviderView updateProvider(AuthenticatedUser actor,String id,AdminDtos.ProviderRequest r){
        AiProvider p=requireProvider(id); if(!p.getCode().equals(r.code())) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        p.update(r.displayName(),r.type(),r.baseUrl(),r.credentialRef(),r.configJson());
        audit.record(actor,AdminAuditAction.PROVIDER_UPDATE,null,"AI_PROVIDER",id,null);return providerView(p);
    }
    @Transactional public AdminDtos.ProviderView providerStatus(AuthenticatedUser actor,String id,boolean enabled){AiProvider p=requireProvider(id);if(enabled)p.enable();else p.disable();
        audit.record(actor,AdminAuditAction.PROVIDER_STATUS_UPDATE,null,"AI_PROVIDER",id,"{\"enabled\":"+enabled+"}");return providerView(p);}

    @Transactional(readOnly=true) public List<AdminDtos.ModelView> models(){return models.findAll(Sort.by("sortOrder")).stream().map(this::modelView).toList();}
    @Transactional public AdminDtos.ModelView createModel(AuthenticatedUser actor,AdminDtos.ModelRequest r){
        if(models.findByCode(r.code()).isPresent()) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        AiModel m=models.save(new AiModel(requireProvider(r.providerId()),r.code(),r.externalModelId(),r.displayName(),r.type())); update(m,r);
        audit.record(actor,AdminAuditAction.MODEL_CREATE,null,"AI_MODEL",m.getPublicId(),null);return modelView(m);
    }
    @Transactional public AdminDtos.ModelView updateModel(AuthenticatedUser actor,String id,AdminDtos.ModelRequest r){AiModel m=requireModel(id);
        if(!m.getCode().equals(r.code())||!m.getProvider().getPublicId().equals(r.providerId())) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        update(m,r);audit.record(actor,AdminAuditAction.MODEL_UPDATE,null,"AI_MODEL",id,null);return modelView(m);}
    @Transactional public AdminDtos.ModelView modelStatus(AuthenticatedUser actor,String id,boolean enabled){AiModel m=requireModel(id);if(enabled)m.enable();else m.disable();
        audit.record(actor,AdminAuditAction.MODEL_STATUS_UPDATE,null,"AI_MODEL",id,"{\"enabled\":"+enabled+"}");return modelView(m);}
    @Transactional(readOnly=true) public PageResult<AdminDtos.AiRequestView> requestLogs(int page,int size,String userId,AiRequestStatus status,String provider,String model){
        return PageResult.from(requests.findAdmin(blank(userId),status,blank(provider),blank(model),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt"))).map(r->
                new AdminDtos.AiRequestView(r.getRequestId(),r.getUser().getPublicId(),r.getConversation().getPublicId(),r.getProviderCode(),r.getModelCode(),r.getStatus(),r.getLatencyMs(),r.getPromptTokens(),r.getCompletionTokens(),r.getTotalTokens(),r.getErrorCode(),r.getCreatedAt())));
    }
    private void update(AiModel m,AdminDtos.ModelRequest r){m.update(r.displayName(),r.externalModelId(),r.type(),defaultJson(r.capabilitiesJson(),"[]"),r.contextWindow(),r.maxOutputTokens(),r.inputPrice(),r.outputPrice(),r.currency(),defaultJson(r.parameterPolicyJson(),null),r.sortOrder());}
    private AdminDtos.ProviderView providerView(AiProvider p){return new AdminDtos.ProviderView(p.getPublicId(),p.getCode(),p.getDisplayName(),p.getProviderType(),p.getBaseUrl(),p.hasCredential(),p.getNonSecretConfigJson(),p.getStatus(),p.getHealthStatus(),p.getUpdatedAt());}
    private AdminDtos.ModelView modelView(AiModel m){return new AdminDtos.ModelView(m.getPublicId(),m.getProvider().getPublicId(),m.getCode(),m.getExternalModelId(),m.getDisplayName(),m.getModelType(),m.getCapabilitiesJson(),m.getContextWindow(),m.getMaxOutputTokens(),m.getInputPrice(),m.getOutputPrice(),m.getCurrency(),m.getParameterPolicyJson(),m.getStatus(),m.getSortOrder(),m.getUpdatedAt());}
    private AiProvider requireProvider(String id){return providers.findByPublicId(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private AiModel requireModel(String id){return models.findByPublicId(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();} private String defaultJson(String v,String fallback){return v==null||v.isBlank()?fallback:v;}
}
