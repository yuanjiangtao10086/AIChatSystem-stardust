package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.service.ValidatedUpload;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFileManagementTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired UserFileRepository files;
    @Autowired AdminAuditLogRepository audits;
    @Autowired FilePersistenceService filePersistence;
    @Autowired StorageService storage;

    @Test
    void normalUserCannotAccessAdminFileApi() throws Exception {
        Account normal = register("file-normal");
        mvc.perform(get("/api/v1/admin/files").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/files/does-not-matter").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminCanListFilterAndInspectFileMetadataWithoutLearningStoragePath() throws Exception {
        Account owner = register("file-owner", "FileOwner" + UUID.randomUUID().toString().substring(0, 6));
        StoredFile report = store(owner, "alpha-report.txt", "text/plain", 120);
        StoredFile photo = store(owner, "beta-photo.png", "image/png", 4096);

        Account admin = register("file-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/files").param("userId", owner.publicId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(report.id())))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(photo.id())));

        // file name search
        mvc.perform(get("/api/v1/admin/files").param("search", "alpha-report").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(report.id())))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(photo.id()))));

        // owner (display name) search
        mvc.perform(get("/api/v1/admin/files").param("userSearch", owner.displayName()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(report.id())));

        // mime prefix filter
        mvc.perform(get("/api/v1/admin/files").param("mime", "image/").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(photo.id())))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(report.id()))));

        // size range filter
        mvc.perform(get("/api/v1/admin/files").param("minSize", "1000").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(photo.id())))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(report.id()))));
        mvc.perform(get("/api/v1/admin/files").param("maxSize", "1000").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(report.id())))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(photo.id()))));

        // upload time range filter
        mvc.perform(get("/api/v1/admin/files")
                        .param("from", Instant.now().minusSeconds(3600).toString())
                        .param("to", Instant.now().plusSeconds(3600).toString())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(report.id())));
        mvc.perform(get("/api/v1/admin/files")
                        .param("to", Instant.now().minusSeconds(3600).toString())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(report.id()))));

        // detail: metadata, owner, references — and never the internal storage path
        String body = mvc.perform(get("/api/v1/admin/files/{id}", report.id()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(report.id()))
                .andExpect(jsonPath("$.data.userId").value(owner.publicId()))
                .andExpect(jsonPath("$.data.userEmail").value(owner.email()))
                .andExpect(jsonPath("$.data.detectedMime").value("text/plain"))
                .andExpect(jsonPath("$.data.sizeBytes").value(120))
                .andExpect(jsonPath("$.data.referenced").value(false))
                .andExpect(jsonPath("$.data.attachmentCount").value(0))
                .andExpect(jsonPath("$.data.knowledgeDocumentCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("objectKey").doesNotContain(report.objectKey());

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_USER_FILE
                && report.id().equals(log.getTargetResourceId()) && log.getRequestId() != null);
    }

    @Test
    void adminDownloadStreamsThroughStorageServiceAndIsAudited() throws Exception {
        Account owner = register("file-dl-owner");
        StoredFile file = store(owner, "secret-notes.txt", "text/plain", 64);
        Account admin = register("file-dl-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        MvcResult result = mvc.perform(get("/api/v1/admin/files/{id}/download", file.id())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(file.content());
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.FILE_DOWNLOAD
                && file.id().equals(log.getTargetResourceId()));
    }

    @Test
    void adminDeleteRemovesPayloadFromStorageAndIsAudited() throws Exception {
        Account owner = register("file-del-owner");
        StoredFile file = store(owner, "violating-upload.txt", "text/plain", 48);
        Account admin = register("file-del-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(delete("/api/v1/admin/files/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isNoContent());

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.FILE_DELETE
                && file.id().equals(log.getTargetResourceId()));
        assertThat(files.findByPublicIdAndDeletedAtIsNull(file.id())).isEmpty();
        // the payload really left the storage backend (deletion went through StorageService)
        assertThatThrownBy(() -> storage.open(file.objectKey())).isInstanceOf(IOException.class);
        mvc.perform(get("/api/v1/admin/files/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotReadOrDeleteSuperAdminFiles() throws Exception {
        Account superAdmin = register("file-sa");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        StoredFile file = store(superAdmin, "super-private.txt", "text/plain", 32);

        Account admin = register("file-limited");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        mvc.perform(get("/api/v1/admin/files/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/admin/files/{id}/download", file.id()).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/admin/files/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void traversingObjectKeyIsNeverOpened() throws Exception {
        Account owner = register("file-trav-owner");
        // metadata only: a traversing key must never reach the filesystem
        StoredFile file = store(owner, "escape.txt", "text/plain", new byte[] {1, 2, 3},
                "../../../../etc/passwd", false);

        Account admin = register("file-trav-admin");
        grant(admin.userId(), "ADMIN");
        String auth = bearer(admin.token());

        MvcResult result = mvc.perform(get("/api/v1/admin/files/{id}/download", file.id())
                        .header("Authorization", auth))
                .andExpect(status().is5xxServerError())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("root:");
    }

    private StoredFile store(Account owner, String name, String mime, int size) throws Exception {
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) ('a' + (i % 26));
        }
        return store(owner, name, mime, content, null, true);
    }

    private StoredFile store(Account owner, String name, String mime, byte[] content,
                             String objectKey, boolean writePayload) throws Exception {
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "bin";
        ValidatedUpload upload = new ValidatedUpload(name, extension, mime, mime, content.length,
                "a".repeat(64), "{}");
        String storageName = PublicIdGenerator.newUlid() + "." + extension;
        String key = objectKey != null ? objectKey : owner.publicId() + "/2026/01/" + storageName;
        UserFile pending = filePersistence.reserve(owner.userId(), upload, storageName, key,
                storage.providerKey());
        if (writePayload) {
            storage.put(key, new ByteArrayInputStream(content));
        }
        String id = filePersistence.complete(owner.userId(), pending.getPublicId()).getPublicId();
        return new StoredFile(id, key, content);
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
        return new Account(user.getId(), user.getPublicId(), token, displayName, email);
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

    private record Account(Long userId, String publicId, String token, String displayName, String email) {
    }

    private record StoredFile(String id, String objectKey, byte[] content) {
    }
}
