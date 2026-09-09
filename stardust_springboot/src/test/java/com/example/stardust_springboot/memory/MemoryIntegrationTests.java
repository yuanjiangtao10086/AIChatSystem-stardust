package com.example.stardust_springboot.memory;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.memory.dto.CreateMemoryRequest;
import com.example.stardust_springboot.memory.dto.UpdateMemoryRequest;
import com.example.stardust_springboot.memory.entity.MemoryType;
import com.example.stardust_springboot.memory.service.MemoryExtractor;
import com.example.stardust_springboot.memory.service.MemoryRetriever;
import com.example.stardust_springboot.memory.service.MemoryService;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "app.ai.memory.retrieval-limit=2",
        "app.ai.memory.retrieval-candidate-limit=10",
        "app.ai.memory.token-budget=32"
})
@Transactional
class MemoryIntegrationTests {
    @Autowired MemoryService service;
    @Autowired MemoryRetriever retriever;
    @Autowired MemoryExtractor extractor;
    @Autowired AppUserRepository users;
    AuthenticatedUser owner;
    AuthenticatedUser other;

    @BeforeEach
    void setUp() {
        owner = principal("owner");
        other = principal("other");
    }

    @Test
    void crudSearchOwnershipDisabledAndSoftDelete() {
        var created = service.create(owner, new CreateMemoryRequest(
                "I prefer concise answers", "Concise answers", MemoryType.PREFERENCE, 80, true));
        assertThat(service.get(owner, created.id()).content()).contains("concise");
        assertThat(service.list(owner, 0, 20, "concise", true, null).items()).hasSize(1);
        assertThatThrownBy(() -> service.get(other, created.id())).isInstanceOf(BusinessException.class);
        var updated = service.update(owner, created.id(),
                new UpdateMemoryRequest(null, "Brief responses", null, 90));
        assertThat(updated.importance()).isEqualTo(90);
        assertThat(service.enabled(owner, created.id(), false).enabled()).isFalse();
        assertThat(retriever.retrieve(owner.id(), "concise answers")).isEmpty();
        service.delete(owner, created.id());
        assertThatThrownBy(() -> service.get(owner, created.id())).isInstanceOf(BusinessException.class);
    }

    @Test
    void retrievalUsesRelevanceImportanceTopKAndTokenBudget() {
        service.create(owner, new CreateMemoryRequest("Uses Vue for the Stardust project",
                "Stardust Vue project", MemoryType.PROJECT, 90, true));
        service.create(owner, new CreateMemoryRequest("Prefers blue interfaces",
                "Blue interface preference", MemoryType.PREFERENCE, 70, true));
        service.create(owner, new CreateMemoryRequest("Likes unrelated hiking",
                "Hiking", MemoryType.PREFERENCE, 100, true));
        service.create(owner, new CreateMemoryRequest("Disabled Stardust note",
                "Stardust disabled", MemoryType.EXPLICIT, 100, false));

        var result = retriever.retrieve(owner.id(), "Stardust Vue blue interface");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MemoryRetriever.RetrievedMemory::summary)
                .containsExactly("Stardust Vue project", "Blue interface preference");
    }

    @Test
    void extractorKeepsOnlyDurableNonSensitiveStatements() {
        assertThat(extractor.extract("请记住：我偏好中文回答")).isPresent();
        assertThat(extractor.extract("今天天气不错")).isEmpty();
        assertThat(extractor.extract("请记住我的 API key 是 sk-secret")).isEmpty();
    }

    private AuthenticatedUser principal(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        AppUser user = users.saveAndFlush(new AppUser(
                prefix + suffix + "@test.local", "hash", prefix));
        return new AuthenticatedUser(user.getId(), user.getPublicId(), user.getEmailNormalized(),
                user.getDisplayName(), 0, Set.of("USER"));
    }
}
