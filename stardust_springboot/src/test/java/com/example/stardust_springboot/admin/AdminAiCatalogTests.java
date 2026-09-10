package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.ai.repository.*;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 11E: administrator AI provider / model management.
 *
 * <p>The deployed secret is injected as a test property, so the console can be verified against a
 * real value while the database only ever holds the {@code env:} reference. Every read is asserted
 * to be free of both the secret and the reference, and every mutation is asserted to be audited.
 */
@SpringBootTest(properties = "STARDUST_TEST_PROVIDER_KEY=sk-test-secret-abcd1234")
@AutoConfigureMockMvc
class AdminAiCatalogTests {

    private static final String SECRET = "sk-test-secret-abcd1234";
    private static final String REFERENCE = "env:STARDUST_TEST_PROVIDER_KEY";
    private static final String MASK = "sk-****1234";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired AiProviderRepository providers;
    @Autowired AiModelRepository models;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void providerReadsExposeOnlyAMaskAndNeverTheApiKey() throws Exception {
        String auth = adminBearer("ai-mask");
        String code = unique("mask-provider");

        MvcResult created = mvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(code, REFERENCE, 60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.maskedApiKey").value(MASK))
                .andExpect(jsonPath("$.data.timeoutSeconds").value(60))
                .andExpect(jsonPath("$.data.connectTimeoutSeconds").value(10))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(REFERENCE))))
                .andReturn();
        String providerId = read(created, "$.data.id");

        // the list must be just as safe as the single-resource responses
        mvc.perform(get("/api/v1/admin/ai/providers").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='" + code + "')].maskedApiKey").value(hasItem(MASK)))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(REFERENCE))));

        // the audit trail records the change without the credential reference or the key
        AdminAuditLog log = audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.PROVIDER_CREATE, providerId);
        assertThat(log.getMetadataJson()).contains("\"credentialConfigured\":true")
                .doesNotContain(SECRET).doesNotContain("STARDUST_TEST_PROVIDER_KEY");
    }

    @Test
    void rawApiKeyIsRejectedSoNoPlaintextSecretReachesTheDatabase() throws Exception {
        String auth = adminBearer("ai-raw");
        long before = providers.count();

        mvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(unique("raw-provider"), "sk-live-abcdefghijklmnop", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        assertThat(providers.count()).isEqualTo(before);
        assertThat(providers.findAll()).noneMatch(p -> p.hasCredential() && p.getCredentialRef().startsWith("sk-"));
    }

    @Test
    void blankApiKeyOnUpdateKeepsTheStoredCredential() throws Exception {
        String auth = adminBearer("ai-keep");
        String code = unique("keep-provider");
        String providerId = read(mvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(code, REFERENCE, 60)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        // blank reference: the stored credential survives, the timeout is cleared
        mvc.perform(put("/api/v1/admin/ai/providers/{id}", providerId)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(code, "", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.maskedApiKey").value(MASK))
                .andExpect(jsonPath("$.data.timeoutSeconds").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET))));

        // a new reference replaces it; an unresolvable scheme is configured but has no mask
        String rotated = "vault:rotated-" + UUID.randomUUID();
        mvc.perform(put("/api/v1/admin/ai/providers/{id}", providerId)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(code, rotated, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.maskedApiKey").doesNotExist())
                .andExpect(jsonPath("$.data.timeoutSeconds").value(30))
                .andExpect(content().string(not(containsString(rotated))));

        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.PROVIDER_UPDATE, providerId).getMetadataJson())
                .contains("\"credentialReplaced\":true").doesNotContain(rotated).doesNotContain(SECRET);
    }

    @Test
    void providerAndModelLifecycleIsAuditedAndDeletionRespectsDependencies() throws Exception {
        String auth = adminBearer("ai-lifecycle");
        String code = unique("life-provider");
        String providerId = read(mvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody(code, null, null)))
                .andExpect(status().isCreated())
                // a provider may legitimately have no credential (local/self-hosted deployment)
                .andExpect(jsonPath("$.data.hasApiKey").value(false))
                .andExpect(jsonPath("$.data.maskedApiKey").doesNotExist())
                .andReturn(), "$.data.id");
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.PROVIDER_CREATE, providerId).getMetadataJson())
                .contains("\"credentialConfigured\":false");

        mvc.perform(patch("/api/v1/admin/ai/providers/{id}/status", providerId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(patch("/api/v1/admin/ai/providers/{id}/status", providerId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENABLED"));
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.PROVIDER_STATUS_UPDATE, providerId))
                .isNotNull();

        String modelId = read(mvc.perform(post("/api/v1/admin/ai/models").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelBody(providerId, unique("life-model"), "CHAT", 10)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        // a provider that still owns models cannot be deleted
        mvc.perform(delete("/api/v1/admin/ai/providers/{id}", providerId).header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));

        mvc.perform(patch("/api/v1/admin/ai/models/{id}/status", modelId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));

        mvc.perform(delete("/api/v1/admin/ai/models/{id}", modelId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.MODEL_DELETE, modelId)).isNotNull();

        mvc.perform(delete("/api/v1/admin/ai/providers/{id}", providerId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.PROVIDER_DELETE, providerId)).isNotNull();
        assertThat(providers.findByPublicId(providerId)).isEmpty();
    }

    @Test
    void modelCapabilitiesAndParametersAreTypedInTheConsole() throws Exception {
        String auth = adminBearer("ai-caps");
        String providerId = createProvider(auth, unique("caps-provider"));
        String code = unique("caps-model");

        MvcResult created = mvc.perform(post("/api/v1/admin/ai/models").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(modelBody(providerId, code, "CHAT", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capabilities.streaming").value(true))
                .andExpect(jsonPath("$.data.capabilities.reasoning").value(true))
                .andExpect(jsonPath("$.data.capabilities.vision").value(false))
                .andExpect(jsonPath("$.data.capabilities.embedding").value(false))
                .andExpect(jsonPath("$.data.contextWindow").value(128000))
                .andExpect(jsonPath("$.data.maxOutputTokens").value(4096))
                .andExpect(jsonPath("$.data.defaultTemperature").value(0.7))
                .andExpect(jsonPath("$.data.defaultMaxOutputTokens").value(2048))
                .andExpect(jsonPath("$.data.providerName").value(startsWith("Provider ")))
                // a new entry is never silently live: an administrator has to enable it
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.defaultModel").value(false))
                .andReturn();
        String modelId = read(created, "$.data.id");

        // the typed object replaced the legacy capability array on write
        assertThat(models.findByPublicId(modelId).orElseThrow().getCapabilitiesJson())
                .contains("\"streaming\":true").doesNotContain("[");

        String updated = modelBody(providerId, code, "MULTIMODAL", 20)
                .replace("\"vision\":false", "\"vision\":true")
                .replace("\"defaultTemperature\":0.7", "\"defaultTemperature\":1.2");
        mvc.perform(put("/api/v1/admin/ai/models/{id}", modelId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MULTIMODAL"))
                .andExpect(jsonPath("$.data.capabilities.vision").value(true))
                .andExpect(jsonPath("$.data.defaultTemperature").value(1.2))
                .andExpect(jsonPath("$.data.sortOrder").value(20));
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.MODEL_UPDATE, modelId)).isNotNull();

        // code and provider are stable references, they cannot be re-pointed
        mvc.perform(put("/api/v1/admin/ai/models/{id}", modelId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelBody(providerId, unique("other-model"), "CHAT", 10)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void onlyOneModelPerTypeIsThePlatformDefaultAndItDrivesTheUserFacingCatalog() throws Exception {
        String auth = adminBearer("ai-default");
        String providerId = createProvider(auth, unique("default-provider"));
        String first = createModel(auth, providerId, unique("default-a"), "CHAT", 10);
        String second = createModel(auth, providerId, unique("default-b"), "CHAT", 20);
        // a freshly registered model stays disabled until an administrator enables it
        enableModel(auth, first);
        enableModel(auth, second);

        mvc.perform(patch("/api/v1/admin/ai/models/{id}/default", first).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"defaultModel\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultModel").value(true));

        mvc.perform(patch("/api/v1/admin/ai/models/{id}/default", second).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"defaultModel\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultModel").value(true));
        assertThat(models.findByPublicId(first).orElseThrow().isDefault()).isFalse();
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.MODEL_DEFAULT_UPDATE, second)).isNotNull();

        // end users only see models of an enabled provider, so a disabled one stays hidden
        assertThat(userCatalogIds(auth)).doesNotContain(second);

        // the chat catalog then pre-selects the default for end users
        enableProvider(auth, providerId);
        mvc.perform(get("/api/v1/ai/models").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + second + "')].defaultModel").value(hasItem(true)));

        // disabling the default clears the flag so a disabled model is never advertised as default
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/status", second).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultModel").value(false));
        assertThat(models.findByPublicId(second).orElseThrow().isDefault()).isFalse();

        // only an enabled model may become the default
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/default", second).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"defaultModel\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void movingAModelReordersItAmongItsProviderSiblings() throws Exception {
        String auth = adminBearer("ai-order");
        String providerId = createProvider(auth, unique("order-provider"));
        String prefix = unique("order");
        String a = createModel(auth, providerId, prefix + "-a", "CHAT", 10);
        createModel(auth, providerId, prefix + "-b", "CHAT", 20);
        String c = createModel(auth, providerId, prefix + "-c", "CHAT", 30);

        assertThat(modelCodesOf(auth, providerId)).containsExactly(prefix + "-a", prefix + "-b", prefix + "-c");

        mvc.perform(patch("/api/v1/admin/ai/models/{id}/order", c).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk());
        assertThat(modelCodesOf(auth, providerId)).containsExactly(prefix + "-a", prefix + "-c", prefix + "-b");
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction.MODEL_REORDER, c)).isNotNull();

        // moving the first model up is a no-op rather than an error
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/order", a).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk());
        assertThat(modelCodesOf(auth, providerId)).containsExactly(prefix + "-a", prefix + "-c", prefix + "-b");

        mvc.perform(patch("/api/v1/admin/ai/models/{id}/order", a).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"SIDEWAYS\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void normalUserCannotManageTheAiCatalog() throws Exception {
        Account user = register("ai-normal");
        String auth = bearer(user.token());

        mvc.perform(get("/api/v1/admin/ai/providers").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/v1/admin/ai/providers").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(providerBody(unique("nope"), null, null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/ai/models").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/v1/admin/ai/models").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/default", UUID.randomUUID()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"defaultModel\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/order", UUID.randomUUID()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"UP\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/ai/providers/{id}", UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    // ------------------------------------------------------------------ helpers

    private String adminBearer(String prefix) throws Exception {
        Account admin = register(prefix);
        AppUser user = users.findById(admin.userId()).orElseThrow();
        Role role = roles.findByCode("ADMIN").orElseThrow();
        if (!userRoles.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            userRoles.saveAndFlush(new UserRole(user, role, null));
        }
        return bearer(admin.token());
    }

    private String createProvider(String auth, String code) throws Exception {
        return read(mvc.perform(post("/api/v1/admin/ai/providers").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(providerBody(code, null, null)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String createModel(String auth, String providerId, String code, String type, int sortOrder) throws Exception {
        return read(mvc.perform(post("/api/v1/admin/ai/models").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(modelBody(providerId, code, type, sortOrder)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private void enableProvider(String auth, String id) throws Exception {
        mvc.perform(patch("/api/v1/admin/ai/providers/{id}/status", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    private List<String> userCatalogIds(String auth) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/ai/models").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].id");
    }

    private void enableModel(String auth, String id) throws Exception {
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/status", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    private List<String> modelCodesOf(String auth, String providerId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/admin/ai/models").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> rows = JsonPath.read(result.getResponse().getContentAsString(), "$.data");
        return rows.stream().filter(row -> providerId.equals(row.get("providerId")))
                .map(row -> (String) row.get("code")).toList();
    }

    private String providerBody(String code, String credentialRef, Integer timeoutSeconds) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"Provider " + code.substring(0, 8)
                + "\",\"type\":\"OPENAI_COMPATIBLE\",\"baseUrl\":\"https://example.invalid/v1\","
                + (credentialRef == null ? "" : "\"credentialRef\":\"" + credentialRef + "\",")
                + (timeoutSeconds == null ? "" : "\"timeoutSeconds\":" + timeoutSeconds + ",")
                + "\"connectTimeoutSeconds\":10}";
    }

    private String modelBody(String providerId, String code, String type, int sortOrder) {
        return "{\"providerId\":\"" + providerId + "\",\"code\":\"" + code + "\",\"externalModelId\":\"" + code
                + "-ext\",\"displayName\":\"Model " + code + "\",\"type\":\"" + type + "\","
                + "\"capabilities\":{\"streaming\":true,\"vision\":false,\"reasoning\":true,\"embedding\":false},"
                + "\"contextWindow\":128000,\"maxOutputTokens\":4096,\"defaultTemperature\":0.7,\"defaultTopP\":1,"
                + "\"defaultMaxOutputTokens\":2048,\"inputPrice\":1.5,\"outputPrice\":6,\"currency\":\"USD\","
                + "\"sortOrder\":" + sortOrder + "}";
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword!123\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated()).andReturn();
        AppUser user = users.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        return new Account(user.getId(), user.getPublicId(),
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(Long userId, String publicId, String token) {
    }
}
