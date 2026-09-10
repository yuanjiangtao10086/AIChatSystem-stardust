package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.conversation.entity.*;
import com.example.stardust_springboot.conversation.repository.*;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminConversationManagementTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired ConversationRepository conversations;
    @Autowired ChatMessageRepository messages;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void normalUserCannotAccessAdminConversationApi() throws Exception {
        Account normal = register("conv-normal");
        mvc.perform(get("/api/v1/admin/conversations").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/conversations/does-not-matter").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void bannedAdminCannotAccessConversationApi() throws Exception {
        Account admin = register("conv-banned-admin");
        grant(admin.userId(), "ADMIN");
        AppUser entity = users.findById(admin.userId()).orElseThrow();
        entity.changeStatus(UserStatus.BANNED, "revoked", null);
        users.saveAndFlush(entity);
        mvc.perform(get("/api/v1/admin/conversations").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void adminCanListSearchFilterAndInspectConversationAndMessages() throws Exception {
        Account owner = register("conv-owner", "OwnerAlpha" + UUID.randomUUID().toString().substring(0, 6));
        AppUser ownerUser = users.findById(owner.userId()).orElseThrow();
        Conversation conv = conversations.saveAndFlush(new Conversation(ownerUser, "Vacation planning thread"));
        ChatMessage q = new ChatMessage(conv, ownerUser, MessageRole.USER, 1, 0,
                "UNIQUE_SEARCH_MARKER how should I book flights?");
        q.complete(Instant.now(), 10L, 20L);
        messages.saveAndFlush(q);
        ChatMessage a = new ChatMessage(conv, ownerUser, MessageRole.ASSISTANT, 2, 0,
                "Here is a **markdown** plan with `code`.");
        a.complete(Instant.now(), 12L, 30L);
        messages.saveAndFlush(a);
        String convId = conv.getPublicId();

        Account admin = register("conv-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        // list shows the conversation (scoped by owner to avoid pagination flakiness)
        mvc.perform(get("/api/v1/admin/conversations").param("userId", owner.publicId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // search by conversation title
        mvc.perform(get("/api/v1/admin/conversations").param("search", "Vacation").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // search by user display name
        mvc.perform(get("/api/v1/admin/conversations").param("search", owner.displayName()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // search by message content
        mvc.perform(get("/api/v1/admin/conversations").param("search", "UNIQUE_SEARCH_MARKER").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // filter by user id
        mvc.perform(get("/api/v1/admin/conversations").param("userId", owner.publicId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // date range filter
        mvc.perform(get("/api/v1/admin/conversations")
                        .param("from", conv.getCreatedAt().minusSeconds(60).toString())
                        .param("to", conv.getCreatedAt().plusSeconds(60).toString())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(convId)));

        // detail view audits VIEW_CONVERSATION and embeds messages
        mvc.perform(get("/api/v1/admin/conversations/{id}", convId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(convId))
                .andExpect(jsonPath("$.data.messages.items[*].id", hasItem(q.getPublicId())));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_CONVERSATION
                && convId.equals(log.getTargetResourceId()) && log.getRequestId() != null);

        // messages view audits VIEW_CHAT_MESSAGES
        mvc.perform(get("/api/v1/admin/conversations/{id}/messages", convId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(a.getPublicId())));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_CHAT_MESSAGES
                && convId.equals(log.getTargetResourceId()));
    }

    @Test
    void adminCannotAccessSuperAdminPrivateConversations() throws Exception {
        Account sa = register("conv-sa", "SuperOwner");
        grant(sa.userId(), "SUPER_ADMIN");
        AppUser saUser = users.findById(sa.userId()).orElseThrow();
        Conversation conv = conversations.saveAndFlush(new Conversation(saUser, "Super secret thread"));
        ChatMessage m = new ChatMessage(conv, saUser, MessageRole.USER, 1, 0, "top secret");
        m.complete(Instant.now(), 1L, 2L);
        messages.saveAndFlush(m);
        String convId = conv.getPublicId();
        String msgId = m.getPublicId();

        Account admin = register("conv-limited");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/conversations/{id}", convId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/conversations/{id}/messages", convId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/conversations/{id}", convId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/messages/{id}", msgId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminCanDeleteConversationAndMessageWithAudit() throws Exception {
        Account owner = register("conv-del-owner", "DelOwner");
        AppUser ownerUser = users.findById(owner.userId()).orElseThrow();
        Conversation conv = conversations.saveAndFlush(new Conversation(ownerUser, "To be cleaned"));
        ChatMessage m1 = new ChatMessage(conv, ownerUser, MessageRole.USER, 1, 0, "bad message one");
        m1.complete(Instant.now(), 1L, 2L);
        messages.saveAndFlush(m1);
        ChatMessage m2 = new ChatMessage(conv, ownerUser, MessageRole.ASSISTANT, 2, 0, "bad message two");
        m2.complete(Instant.now(), 1L, 2L);
        messages.saveAndFlush(m2);
        String convId = conv.getPublicId();
        String m1Id = m1.getPublicId();

        Account admin = register("conv-del-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        // delete single message
        mvc.perform(delete("/api/v1/admin/messages/{id}", m1Id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.DELETE_CHAT_MESSAGE
                && m1Id.equals(log.getTargetResourceId()));
        mvc.perform(get("/api/v1/admin/conversations/{id}/messages", convId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(m1Id))));

        // delete whole conversation
        mvc.perform(delete("/api/v1/admin/conversations/{id}", convId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.DELETE_CONVERSATION
                && convId.equals(log.getTargetResourceId()));
        mvc.perform(get("/api/v1/admin/conversations/{id}", convId).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    private Account register(String prefix) throws Exception {
        return register(prefix, "Tester");
    }

    private Account register(String prefix, String displayName) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword!123\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        AppUser user = users.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        return new Account(user.getId(), user.getPublicId(), token, displayName);
    }

    private void grant(Long userId, String roleCode) {
        AppUser user = users.findById(userId).orElseThrow();
        Role role = roles.findByCode(roleCode).orElseThrow();
        if (!userRoles.existsByUserIdAndRoleId(userId, role.getId())) {
            userRoles.saveAndFlush(new UserRole(user, role, null));
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(Long userId, String publicId, String token, String displayName) {
    }
}
