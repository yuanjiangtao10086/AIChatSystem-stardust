package com.example.stardust_springboot.file;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage.default-quota-bytes=20")
@AutoConfigureMockMvc
class UserFileIntegrationTests {
    private static final String PASSWORD = "StrongPassword!123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadListSearchRenameDownloadPreviewDeleteAndUsageWork() throws Exception {
        String token = register("file-crud");
        String fileId = upload(token, "../../field-note.txt", "text/plain", "hello".getBytes()).getId();

        mockMvc.perform(get("/api/v1/files").header("Authorization", bearer(token)).param("search", "FIELD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(fileId))
                .andExpect(jsonPath("$.data.items[0].name").value("field-note.txt"))
                .andExpect(jsonPath("$.data.items[0].storageProvider").value("local"));
        mockMvc.perform(get("/api/v1/files/usage").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.usedBytes").value(5))
                .andExpect(jsonPath("$.data.reservedBytes").value(0))
                .andExpect(jsonPath("$.data.fileCount").value(1));
        mockMvc.perform(patch("/api/v1/files/{id}", fileId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"name\":\"renamed.txt\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("renamed.txt"));
        mockMvc.perform(get("/api/v1/files/{id}/download", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(content().bytes("hello".getBytes()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));

        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        String imageId = upload(token, "preview.png", "image/png", pngHeader).getId();
        mockMvc.perform(get("/api/v1/files/{id}/preview", imageId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(content().bytes(pngHeader))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));

        mockMvc.perform(delete("/api/v1/files/{id}", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/files/{id}", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/usage").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.usedBytes").value(8))
                .andExpect(jsonPath("$.data.fileCount").value(1));
        mockMvc.perform(delete("/api/v1/files/{id}", imageId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void extensionMimeMagicAndOwnershipAreEnforcedByServer() throws Exception {
        String owner = register("file-owner");
        String attacker = register("file-attacker");
        String fileId = upload(owner, "secret.txt", "text/plain", "secret".getBytes()).getId();

        mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile(
                        "file", "fake.png", "image/png", "not-png".getBytes()))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(41501));
        mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile(
                        "file", "script.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(get("/api/v1/files/{id}", fileId).header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/{id}/download", fileId).header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/files/{id}", fileId).header("Authorization", bearer(attacker))
                        .contentType("application/json").content("{\"name\":\"stolen.txt\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/files/{id}", fileId).header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/files/{id}", fileId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void concurrentUploadsReserveQuotaWithoutOvercommit() throws Exception {
        String token = register("file-race");
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentUpload(token, start, "one.txt"));
            var second = executor.submit(() -> concurrentUpload(token, start, "two.txt"));
            start.countDown();
            statuses.add(first.get());
            statuses.add(second.get());
        }
        assertThat(statuses).containsExactlyInAnyOrder(201, 429);
        mockMvc.perform(get("/api/v1/files/usage").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.usedBytes").value(11))
                .andExpect(jsonPath("$.data.reservedBytes").value(0))
                .andExpect(jsonPath("$.data.fileCount").value(1));
    }

    private int concurrentUpload(String token, CountDownLatch start, String name) throws Exception {
        start.await();
        return mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile(
                        "file", name, "text/plain", "12345678901".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getStatus();
    }

    private Uploaded upload(String token, String name, String mime, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", name, mime, bytes))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        return new Uploaded(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"));
    }

    private String register(String prefix) throws Exception {
        String email = (prefix + "-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s","displayName":"File tester"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Uploaded(String getId) { }
}
