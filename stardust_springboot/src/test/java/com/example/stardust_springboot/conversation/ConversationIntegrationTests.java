package com.example.stardust_springboot.conversation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationIntegrationTests {

    private static final String PASSWORD = "StrongPassword!123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conversationCrudSearchPaginationAndEmptyMessageHistoryWork() throws Exception {
        String token = register("chat-owner");
        String firstId = createConversation(token, "Project Aurora");
        String secondId = createConversation(token, "Reading notes");

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(token))
                        .param("search", "AURORA")
                        .param("sort", "TITLE")
                        .param("direction", "ASC")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(firstId))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(patch("/api/v1/conversations/{id}", firstId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Aurora field log\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Aurora field log"));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", firstId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/conversations/{id}", firstId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageCount").value(0));

        mockMvc.perform(delete("/api/v1/conversations/{id}", secondId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/conversations/{id}", secondId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void everyConversationAndMessageRouteEnforcesOwnership() throws Exception {
        String ownerToken = register("owner");
        String attackerToken = register("attacker");
        String ownerConversationId = createConversation(ownerToken, "Owner only");
        String attackerConversationId = createConversation(attackerToken, "Attacker notes");

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(attackerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(attackerConversationId));

        assertHidden(get("/api/v1/conversations/{id}", ownerConversationId), attackerToken);
        assertHidden(patch("/api/v1/conversations/{id}", ownerConversationId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stolen\"}"), attackerToken);
        assertHidden(delete("/api/v1/conversations/{id}", ownerConversationId), attackerToken);
        assertHidden(get("/api/v1/conversations/{id}/messages", ownerConversationId), attackerToken);
        assertHidden(post("/api/v1/conversations/{id}/messages:stream", ownerConversationId)
                .header("Idempotency-Key", "attacker-request-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"intrusion\",\"modelId\":\"00000000000000000000000000\"}"),
                attackerToken);

        mockMvc.perform(get("/api/v1/conversations/{id}", ownerConversationId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Owner only"));
    }

    private void assertHidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                              String accessToken) throws Exception {
        mockMvc.perform(request.header("Authorization", bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    private String register(String prefix) throws Exception {
        String email = (prefix + "-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Chat tester"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String createConversation(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
