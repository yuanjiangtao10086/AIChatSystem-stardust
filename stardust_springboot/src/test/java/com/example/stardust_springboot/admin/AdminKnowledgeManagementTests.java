package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.service.ValidatedUpload;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.gateway.*;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 11D: administrator knowledge base / RAG management.
 *
 * Every AI operation (parse, embed, vector delete) is executed by Spring through
 * {@link RagGateway}; the gateway is faked here so no Python service is required, and Vue
 * never talks to Python — it only calls {@code /api/v1/admin/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKnowledgeManagementTests.FakeRagConfiguration.class)
class AdminKnowledgeManagementTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired AdminAuditLogRepository audits;
    @Autowired FilePersistenceService filePersistence;
    @Autowired StorageService storage;
    @Autowired FakeRagGateway gateway;

    @Test
    void normalUserCannotAccessAdminKnowledgeApi() throws Exception {
        Account normal = register("kb-normal");
        mvc.perform(get("/api/v1/admin/knowledge-bases").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/knowledge-documents").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminCanListFilterAndInspectKnowledgeBase() throws Exception {
        Account owner = register("kb-owner", "KbOwner" + UUID.randomUUID().toString().substring(0, 6));
        String baseId = createBase(owner, "Operations runbooks");
        String readyDoc = addDocument(owner, baseId, store(owner, "runbook-alpha.txt", 64));
        gateway.failNext.set(true);
        String failedDoc = addDocument(owner, baseId, store(owner, "runbook-beta.txt", 64));

        Account admin = register("kb-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/knowledge-bases").param("userId", owner.publicId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(baseId)))
                .andExpect(jsonPath("$.data.items[0].userName").value(owner.displayName()));

        mvc.perform(get("/api/v1/admin/knowledge-bases").param("userSearch", owner.displayName())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(baseId)));

        mvc.perform(get("/api/v1/admin/knowledge-bases").param("search", "runbooks")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(baseId)));

        // detail carries owner, statistics and the documents of that base
        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", baseId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(baseId))
                .andExpect(jsonPath("$.data.userId").value(owner.publicId()))
                .andExpect(jsonPath("$.data.documentCount").value(2))
                .andExpect(jsonPath("$.data.readyDocumentCount").value(1))
                .andExpect(jsonPath("$.data.failedDocumentCount").value(1))
                .andExpect(jsonPath("$.data.totalChunks").value(1))
                .andExpect(jsonPath("$.data.documents.items[*].id", hasItem(readyDoc)))
                .andExpect(jsonPath("$.data.documents.items[*].id", hasItem(failedDoc)));

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_KNOWLEDGE_BASE
                && baseId.equals(log.getTargetResourceId()) && log.getRequestId() != null);
    }

    @Test
    void adminCanFilterDocumentsByStatusBaseAndOwner() throws Exception {
        Account owner = register("doc-owner", "DocOwner" + UUID.randomUUID().toString().substring(0, 6));
        String baseId = createBase(owner, "Policy library");
        String readyDoc = addDocument(owner, baseId, store(owner, "policy-ready.txt", 32));
        gateway.failNext.set(true);
        String failedDoc = addDocument(owner, baseId, store(owner, "policy-broken.txt", 32));

        Account admin = register("doc-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/knowledge-documents").param("knowledgeBaseId", baseId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(readyDoc)))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(failedDoc)));

        mvc.perform(get("/api/v1/admin/knowledge-documents").param("status", "READY")
                        .param("knowledgeBaseId", baseId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(readyDoc)))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(failedDoc))));

        mvc.perform(get("/api/v1/admin/knowledge-documents").param("status", "FAILED")
                        .param("knowledgeBaseId", baseId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(failedDoc)))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(readyDoc))))
                // failure reason and chunk count are exposed to the administrator
                .andExpect(jsonPath("$.data.items[?(@.id=='" + failedDoc + "')].errorCode").value(hasItem("PROVIDER_ERROR")));

        mvc.perform(get("/api/v1/admin/knowledge-documents").param("search", "policy-broken")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(failedDoc)))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(readyDoc))));

        mvc.perform(get("/api/v1/admin/knowledge-documents").param("userSearch", owner.displayName())
                        .param("knowledgeBaseId", baseId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(readyDoc)));
    }

    @Test
    void adminCanRetryFailedDocumentWithAudit() throws Exception {
        Account owner = register("retry-owner");
        String baseId = createBase(owner, "Retry base");
        gateway.failNext.set(true);
        String docId = addDocument(owner, baseId, store(owner, "retry-me.txt", 48));

        Account admin = register("retry-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(post("/api/v1/admin/knowledge-documents/{id}/retry", docId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(docId))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.chunkCount").value(1));

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.RAG_DOCUMENT_RETRY
                && docId.equals(log.getTargetResourceId()));
    }

    @Test
    void adminCanDeleteDocumentWithAuditAndVectorsLeaveThroughGateway() throws Exception {
        Account owner = register("del-owner");
        String baseId = createBase(owner, "Delete base");
        String docId = addDocument(owner, baseId, store(owner, "delete-me.txt", 40));

        Account admin = register("del-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(delete("/api/v1/admin/knowledge-documents/{id}", docId).header("Authorization", auth))
                .andExpect(status().isNoContent());

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.RAG_DOCUMENT_DELETE
                && docId.equals(log.getTargetResourceId()));
        assertThat(documents.findByPublicIdAndDeletedAtIsNull(docId)).isEmpty();
        // the vector index is dropped through Spring -> Python, not by the browser
        assertThat(gateway.deletedDocuments).contains(docId);
        mvc.perform(get("/api/v1/admin/knowledge-documents").param("knowledgeBaseId", baseId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(docId))));
    }

    @Test
    void adminCanRemoveVectorsKeepingDocumentRecord() throws Exception {
        Account owner = register("vec-owner");
        String baseId = createBase(owner, "Vector base");
        String docId = addDocument(owner, baseId, store(owner, "vector-me.txt", 40));

        Account admin = register("vec-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(delete("/api/v1/admin/knowledge-documents/{id}/vectors", docId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(docId))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("VECTOR_REMOVED"))
                .andExpect(jsonPath("$.data.chunkCount").value(0));

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.RAG_VECTOR_REMOVE
                && docId.equals(log.getTargetResourceId()));
        assertThat(gateway.deletedDocuments).contains(docId);
    }

    @Test
    void adminCannotTouchSuperAdminKnowledge() throws Exception {
        Account superAdmin = register("kb-sa");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        String baseId = createBase(superAdmin, "Super base");
        String docId = addDocument(superAdmin, baseId, store(superAdmin, "super-doc.txt", 32));

        Account admin = register("kb-limited");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/knowledge-bases/{id}", baseId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/v1/admin/knowledge-documents/{id}/retry", docId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/knowledge-documents/{id}", docId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/knowledge-documents/{id}/vectors", docId).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    private String createBase(Account owner, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String addDocument(Account owner, String baseId, StoredFile file) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/knowledge-bases/{baseId}/documents", baseId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + file.id() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private StoredFile store(Account owner, String name, int size) throws Exception {
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) ('a' + (i % 26));
        }
        String extension = name.substring(name.lastIndexOf('.') + 1);
        ValidatedUpload upload = new ValidatedUpload(name, extension, "text/plain", "text/plain",
                content.length, "a".repeat(64), "{}");
        String storageName = PublicIdGenerator.newUlid() + "." + extension;
        String key = owner.publicId() + "/2026/09/" + storageName;
        UserFile pending = filePersistence.reserve(owner.userId(), upload, storageName, key,
                storage.providerKey());
        storage.put(key, new ByteArrayInputStream(content));
        return new StoredFile(filePersistence.complete(owner.userId(), pending.getPublicId()).getPublicId(), key);
    }

    private Account register(String prefix) throws Exception {
        return register(prefix, "Tester");
    }

    private Account register(String prefix, String displayName) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
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

    private record StoredFile(String id, String objectKey) {
    }

    @TestConfiguration
    static class FakeRagConfiguration {
        @Bean
        @Primary
        FakeRagGateway fakeRagGateway() {
            return new FakeRagGateway();
        }
    }

    /** Stands in for the Python AI service: Spring is the only caller of this port. */
    static class FakeRagGateway implements RagGateway {
        final AtomicBoolean failNext = new AtomicBoolean();
        final List<String> deletedDocuments = new CopyOnWriteArrayList<>();

        @Override
        public RagProcessResult process(RagProcessCommand command) {
            if (failNext.compareAndSet(true, false)) {
                throw new RagGatewayException("PROVIDER_ERROR", "simulated provider failure");
            }
            return new RagProcessResult(command.documentId(), command.knowledgeBaseId(), List.of(
                    new RagProcessResult.Chunk(0, "indexed content", 3, 1,
                            Map.of("filename", command.filename()))), "test-embedding");
        }

        @Override
        public RagRetrieveResult retrieve(String userPublicId, List<String> knowledgeBaseIds, String query) {
            return new RagRetrieveResult(List.of());
        }

        @Override
        public void deleteDocument(String userPublicId, String knowledgeBaseId, String documentId) {
            deletedDocuments.add(documentId);
        }
    }
}
