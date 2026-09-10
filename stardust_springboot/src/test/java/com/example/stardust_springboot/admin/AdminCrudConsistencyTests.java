package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.service.ValidatedUpload;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBase;
import com.example.stardust_springboot.knowledge.repository.KnowledgeBaseRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.Role;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the contract that made the admin console show "list has data, detail not found".
 *
 * The browser only ever knows the id it read from the list response, so this test drives the
 * exact same path: call the list endpoint, read {@code $.data.items[0].id} from the JSON body,
 * and immediately use that string for the detail endpoint. Any drift between the id semantics
 * of the list projection and the detail query fails here instead of in the browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCrudConsistencyTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired ConversationRepository conversations;
    @Autowired KnowledgeBaseRepository bases;
    @Autowired FilePersistenceService filePersistence;
    @Autowired StorageService storage;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listThenDetailUsesTheSameConversationId() throws Exception {
        Account owner = register("crud-conv-owner", "CrudConvOwner");
        AppUser ownerUser = users.findById(owner.userId()).orElseThrow();
        Conversation conversation = conversations.saveAndFlush(new Conversation(ownerUser, "Crud matrix thread"));
        Account admin = register("crud-conv-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        String listed = firstId(mvc.perform(get("/api/v1/admin/conversations")
                        .param("userId", owner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userName").value(owner.displayName()))
                .andReturn());

        assertThat(listed).isEqualTo(conversation.getPublicId());

        mvc.perform(get("/api/v1/admin/conversations/{id}", listed).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(listed))
                .andExpect(jsonPath("$.data.userId").value(owner.publicId()));
    }

    @Test
    void listThenDetailUsesTheSameFileId() throws Exception {
        Account owner = register("crud-file-owner", "CrudFileOwner");
        String fileId = store(owner, "crud-matrix.txt", 64);
        Account admin = register("crud-file-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        String listed = firstId(mvc.perform(get("/api/v1/admin/files")
                        .param("userId", owner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userName").value(owner.displayName()))
                .andReturn());

        assertThat(listed).isEqualTo(fileId);

        mvc.perform(get("/api/v1/admin/files/{id}", listed).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(listed))
                .andExpect(jsonPath("$.data.userId").value(owner.publicId()));
    }

    @Test
    void listThenDetailUsesTheSameKnowledgeBaseId() throws Exception {
        Account owner = register("crud-kb-owner", "CrudKbOwner");
        AppUser ownerUser = users.findById(owner.userId()).orElseThrow();
        KnowledgeBase base = bases.saveAndFlush(new KnowledgeBase(ownerUser, "Crud matrix base", "desc"));
        Account admin = register("crud-kb-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        String listed = firstId(mvc.perform(get("/api/v1/admin/knowledge-bases")
                        .param("userId", owner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userName").value(owner.displayName()))
                .andReturn());

        assertThat(listed).isEqualTo(base.getPublicId());

        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", listed).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(listed))
                .andExpect(jsonPath("$.data.userId").value(owner.publicId()));
    }

    /**
     * The original "list has data, detail says not found" symptom: the list applied no ADR-058
     * filter while every detail endpoint did, so an ADMIN saw a SUPER_ADMIN's resources and got
     * 40301 on each click. Listing must never advertise a row the same operator cannot open.
     */
    @Test
    void adminListNeverAdvertisesResourcesItIsForbiddenToOpen() throws Exception {
        Account superOwner = register("crud-sa-owner", "CrudSaOwner");
        grant(superOwner.userId(), "SUPER_ADMIN");
        AppUser superUser = users.findById(superOwner.userId()).orElseThrow();
        Conversation conversation = conversations.saveAndFlush(new Conversation(superUser, "Super private thread"));
        KnowledgeBase base = bases.saveAndFlush(new KnowledgeBase(superUser, "Super private base", "desc"));
        String fileId = store(superOwner, "super-private.txt", 32);

        Account admin = register("crud-limited-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/conversations").param("userId", superOwner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(conversation.getPublicId()))));
        mvc.perform(get("/api/v1/admin/files").param("userId", superOwner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(fileId))));
        mvc.perform(get("/api/v1/admin/knowledge-bases").param("userId", superOwner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(base.getPublicId()))));

        mvc.perform(get("/api/v1/admin/conversations/{id}", conversation.getPublicId())
                        .header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/files/{id}", fileId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", base.getPublicId())
                        .header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void superAdminCanOpenEveryResourceItsListShows() throws Exception {
        Account owner = register("crud-sa-visible-owner", "CrudVisibleOwner");
        AppUser ownerUser = users.findById(owner.userId()).orElseThrow();
        Conversation conversation = conversations.saveAndFlush(new Conversation(ownerUser, "Visible thread"));

        Account superAdmin = register("crud-sa-viewer");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        String auth = bearer(superAdmin.token());

        String listed = firstId(mvc.perform(get("/api/v1/admin/conversations")
                        .param("userId", owner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(conversation.getPublicId())))
                .andReturn());

        mvc.perform(get("/api/v1/admin/conversations/{id}", listed).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversation.getPublicId()));
    }

    @Test
    void unknownIdIsNotFoundForEveryAdminDetailEndpoint() throws Exception {
        Account admin = register("crud-missing-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());
        String missing = "01J0000000000000000000000";

        mvc.perform(get("/api/v1/admin/conversations/{id}", missing).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/files/{id}", missing).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", missing).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void normalUserIsForbiddenOnEveryAdminDetailEndpoint() throws Exception {
        Account normal = register("crud-normal");
        String auth = bearer(normal.token());
        String any = "01J0000000000000000000000";

        mvc.perform(get("/api/v1/admin/conversations/{id}", any).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/files/{id}", any).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", any).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    /**
     * Reproduces data written by an older build (the project seed uses the legacy
     * {@code CONVERSATION_VIEW} action name that stage 11B renamed to {@code VIEW_CHAT_MESSAGES}).
     * {@code @Enumerated(STRING)} throws on unknown values, which turns any read of the audit table
     * into a 50002 "persistence operation failed" that looks nothing like an enum mismatch.
     */
    @Test
    void legacyAuditActionValueWrittenByOlderBuildDoesNotBreakReads() throws Exception {
        Account owner = register("legacy-audit-owner", "LegacyOwner");
        String fileId = store(owner, "Remove image.png", 32);
        Account admin = register("legacy-audit-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        String legacyId = PublicIdGenerator.newUlid();
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("insert into admin_audit_log (public_id, admin_id, action, target_user_id, "
                        + "target_resource_type, target_resource_id, ip, user_agent, request_id, "
                        + "metadata_json, created_at, updated_at, version) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,0)",
                legacyId, admin.userId(), "CONVERSATION_VIEW", owner.userId(), "CONVERSATION",
                legacyId, "127.0.0.1", "legacy-agent", "legacy-request", null, now, now);

        // A file detail must be immune to unrelated legacy audit rows.
        mvc.perform(get("/api/v1/admin/files/{id}", fileId).header("Authorization", auth))
                .andExpect(status().isOk());

        // The audit feed must render rows written by an older build instead of failing.
        mvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", auth))
                .andExpect(status().isOk());
    }

    /**
     * Boundary matrix for the reported symptom: names with spaces (the reported
     * {@code Remove image.png}), non-ASCII names and the image types an operator actually opens.
     * None of them may reach SQL or the audit write unescaped.
     */
    @Test
    void adminFileDetailHandlesSpacesChineseNamesAndImageTypes() throws Exception {
        Account owner = register("edge-file-owner", "EdgeFileOwner");
        Account admin = register("edge-file-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        String[][] cases = {
                {"Remove image.png", "image/png"},
                {"svgimage.png", "image/png"},
                {"vector icon.svg", "image/svg+xml"},
                {"假期照片 2026.jpg", "image/jpeg"},
                {"report with spaces.txt", "text/plain"},
        };
        for (String[] item : cases) {
            String id = store(owner, item[0], item[1], 48);
            mvc.perform(get("/api/v1/admin/files/{id}", id).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.name").value(item[0]))
                    .andExpect(jsonPath("$.data.detectedMime").value(item[1]));
        }
    }

    /** A soft-deleted file must answer 40401 (not found) — never 50002, which reads as a DB fault. */
    @Test
    void softDeletedFileIsReportedAsNotFoundNotPersistenceFailure() throws Exception {
        Account owner = register("deleted-file-owner");
        String fileId = store(owner, "gone.txt", 32);
        Account admin = register("deleted-file-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/files/{id}", fileId).header("Authorization", auth))
                .andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/files/{id}", fileId).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/files/{id}", fileId).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    /** Reads the id exactly the way the Vue table does: from the list response body. */
    private String firstId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.items[0].id");
    }

    private String store(Account owner, String name, int size) throws Exception {
        return store(owner, name, "text/plain", size);
    }

    private String store(Account owner, String name, String mime, int size) throws Exception {
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) ('a' + (i % 26));
        }
        String extension = name.substring(name.lastIndexOf('.') + 1);
        ValidatedUpload upload = new ValidatedUpload(name, extension, mime, mime,
                content.length, "a".repeat(64), "{\"category\":\"image\",\"previewable\":true}");
        String storageName = PublicIdGenerator.newUlid() + "." + extension;
        String key = owner.publicId() + "/2026/09/" + storageName;
        UserFile pending = filePersistence.reserve(owner.userId(), upload, storageName, key,
                storage.providerKey());
        storage.put(key, new ByteArrayInputStream(content));
        return filePersistence.complete(owner.userId(), pending.getPublicId()).getPublicId();
    }

    private Account register(String prefix) throws Exception {
        return register(prefix, "Tester");
    }

    private Account register(String prefix, String displayName) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword!123\",\"displayName\":\""
                                + displayName + "\"}"))
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
