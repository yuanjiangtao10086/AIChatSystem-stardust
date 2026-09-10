package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.admin.audit.AdminAuditLogRepository;
import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.example.stardust_springboot.ai.request.AiRequestLog;
import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.UserRole;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.RoleRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 11F: admin dashboard aggregates, audit trail browsing and AI request log browsing.
 *
 * <p>All three are read-only views over existing tables: the dashboard must never invent numbers
 * (no fake trend), and neither log endpoint may be writable or filterable in a way that hides rows
 * from an administrator.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardAndLogsTests {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired AiProviderRepository providers;
    @Autowired AiModelRepository models;
    @Autowired AiRequestLogRepository requests;
    @Autowired ConversationRepository conversations;
    @Autowired ChatMessageRepository messages;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void dashboardExposesRealAggregatesHourlyTrendAndFeeds() throws Exception {
        Account admin = admin("dash-admin");
        String auth = bearer(admin.token());
        String providerId = createProvider(auth, unique("dash-provider"));
        enableProvider(auth, providerId);
        String modelId = createModel(auth, providerId, unique("dash-model"), "CHAT", 10);
        enableModel(auth, modelId);

        Account owner = register("dash-owner");
        String completed = logRequest(owner, modelId, true);
        String failed = logRequest(owner, modelId, false);

        MvcResult result = mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.aiRequests").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.enabledProviders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.enabledModels").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.hourly", hasSize(24)))
                .andReturn();
        String body = result.getResponse().getContentAsString();

        // the trend is derived from real rows, not a decorative constant
        List<Integer> perHour = JsonPath.read(body, "$.data.hourly[*].requests");
        assertThat(perHour.stream().mapToLong(Integer::longValue).sum()).isGreaterThanOrEqualTo(2);

        // recent failures: newest first, bounded, and our failure is visible
        List<String> failures = JsonPath.read(body, "$.data.recentFailures[*].requestId");
        assertThat(failures).hasSizeLessThanOrEqualTo(5).contains(failed).doesNotContain(completed);

        // recent audits capture the admin actions performed above
        List<String> actions = JsonPath.read(body, "$.data.recentAudits[*].action");
        assertThat(actions).hasSizeLessThanOrEqualTo(5).contains(AdminAuditAction.PROVIDER_CREATE.name());
    }

    @Test
    void auditTrailCanBeFilteredByActorActionTargetAndTime() throws Exception {
        Account admin = admin("audit-admin");
        String auth = bearer(admin.token());
        String providerId = createProvider(auth, unique("audit-provider"));

        MvcResult page = mvc.perform(get("/api/v1/admin/audit-logs")
                        .param("adminId", admin.publicId())
                        .param("action", "PROVIDER_CREATE")
                        .param("targetType", "AI_PROVIDER")
                        .param("size", "50")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].targetResourceId", hasItem(providerId)))
                .andExpect(jsonPath("$.data.items[*].adminId", everyItem(is(admin.publicId()))))
                .andExpect(jsonPath("$.data.items[*].action",
                        everyItem(is(AdminAuditAction.PROVIDER_CREATE.name()))))
                .andReturn();
        assertThat(JsonPath.<List<Object>>read(page.getResponse().getContentAsString(), "$.data.items"))
                .isNotEmpty();

        // a window that ends before the action hides it
        mvc.perform(get("/api/v1/admin/audit-logs").param("targetType", "AI_PROVIDER")
                        .param("to", Instant.now().minusSeconds(3600).toString())
                        .param("size", "50").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].targetResourceId", not(hasItem(providerId))));

        // ... and a window around "now" shows it
        mvc.perform(get("/api/v1/admin/audit-logs").param("targetType", "AI_PROVIDER")
                        .param("from", Instant.now().minusSeconds(3600).toString())
                        .param("to", Instant.now().plusSeconds(3600).toString())
                        .param("size", "50").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].targetResourceId", hasItem(providerId)));

        // paging is honoured and the newest row comes first
        MvcResult firstPage = mvc.perform(get("/api/v1/admin/audit-logs").param("size", "1")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)))
                .andReturn();
        String newest = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.items[0].id");
        assertThat(audits.findFirstByActionAndTargetResourceIdOrderByIdDesc(
                AdminAuditAction.PROVIDER_CREATE, providerId)).isNotNull();
        assertThat(newest).isNotBlank();
    }

    @Test
    void aiRequestLogCanBeFilteredByStatusProviderModelUserAndTime() throws Exception {
        Account admin = admin("reqlog-admin");
        String auth = bearer(admin.token());
        String providerCode = unique("req-provider");
        String providerId = createProvider(auth, providerCode);
        String modelCode = unique("req-model");
        String modelId = createModel(auth, providerId, modelCode, "CHAT", 10);

        Account owner = register("reqlog-owner");
        String completed = logRequest(owner, modelId, true);
        String failed = logRequest(owner, modelId, false);

        mvc.perform(get("/api/v1/admin/ai/requests").param("status", "FAILED").param("size", "50")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].requestId", hasItem(failed)))
                .andExpect(jsonPath("$.data.items[*].requestId", not(hasItem(completed))));

        mvc.perform(get("/api/v1/admin/ai/requests").param("status", "COMPLETED").param("size", "50")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].requestId", hasItem(completed)))
                .andExpect(jsonPath("$.data.items[*].requestId", not(hasItem(failed))));

        mvc.perform(get("/api/v1/admin/ai/requests").param("provider", providerCode)
                        .param("model", modelCode).param("size", "50").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].requestId", hasItems(completed, failed)));

        mvc.perform(get("/api/v1/admin/ai/requests").param("userId", owner.publicId())
                        .param("size", "50").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].requestId", hasItems(completed, failed)));

        mvc.perform(get("/api/v1/admin/ai/requests").param("userId", owner.publicId())
                        .param("to", Instant.now().minusSeconds(3600).toString())
                        .param("size", "50").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].requestId", not(hasItem(completed))));

        // a single row is addressable for the console detail drawer
        mvc.perform(get("/api/v1/admin/ai/requests/{id}", failed).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(failed))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("TEST_FAILURE"));

        mvc.perform(get("/api/v1/admin/ai/requests/{id}", "01HZZZZZZZZZZZZZZZZZZZZZZ")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void normalUserCannotReadDashboardAuditTrailOrRequestLogs() throws Exception {
        Account user = register("logs-normal");
        String auth = bearer(user.token());

        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/ai/requests").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    // ------------------------------------------------------------------ helpers

    private Account admin(String prefix) throws Exception {
        Account account = register(prefix);
        AppUser user = users.findById(account.userId()).orElseThrow();
        var role = roles.findByCode("ADMIN").orElseThrow();
        if (!userRoles.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            userRoles.saveAndFlush(new UserRole(user, role, null));
        }
        return account;
    }

    private String createProvider(String auth, String code) throws Exception {
        return read(mvc.perform(post("/api/v1/admin/ai/providers").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"displayName\":\"Provider " + code.substring(0, 8)
                                + "\",\"type\":\"OPENAI_COMPATIBLE\",\"baseUrl\":\"https://example.invalid/v1\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private void enableProvider(String auth, String id) throws Exception {
        mvc.perform(patch("/api/v1/admin/ai/providers/{id}/status", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    private String createModel(String auth, String providerId, String code, String type, int sortOrder) throws Exception {
        return read(mvc.perform(post("/api/v1/admin/ai/models").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"" + providerId + "\",\"code\":\"" + code + "\","
                                + "\"externalModelId\":\"" + code + "-ext\",\"displayName\":\"Model " + code + "\","
                                + "\"type\":\"" + type + "\",\"capabilities\":{\"streaming\":true,\"vision\":false,"
                                + "\"reasoning\":false,\"embedding\":false},\"sortOrder\":" + sortOrder + "}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private void enableModel(String auth, String id) throws Exception {
        mvc.perform(patch("/api/v1/admin/ai/models/{id}/status", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    /** Persists a real request log row: completed or failed, with the provider/model of the row. */
    private String logRequest(Account owner, String modelId, boolean success) {
        AppUser user = users.findById(owner.userId()).orElseThrow();
        // findCatalog() fetch-joins the provider: AiRequestLog copies provider facts on construction
        AiModel model = models.findCatalog().stream()
                .filter(candidate -> candidate.getPublicId().equals(modelId)).findFirst().orElseThrow();
        Conversation conversation = conversations.saveAndFlush(new Conversation(user, "Log probe"));
        ChatMessage assistant = messages.saveAndFlush(
                new ChatMessage(conversation, user, MessageRole.ASSISTANT, 1, 0, "probe"));
        String requestId = PublicIdGenerator.newUlid();
        AiRequestLog log = requests.saveAndFlush(
                new AiRequestLog(requestId, user, conversation, assistant, model));
        Instant now = Instant.now();
        if (success) {
            log.complete(now, 10L, 20L, 30L);
        } else {
            log.fail(now, "TEST_FAILURE");
        }
        requests.saveAndFlush(log);
        return requestId;
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
