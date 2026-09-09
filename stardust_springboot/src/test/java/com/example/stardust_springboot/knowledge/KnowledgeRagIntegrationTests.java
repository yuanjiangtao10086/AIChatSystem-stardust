package com.example.stardust_springboot.knowledge;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.conversation.dto.CreateConversationRequest;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.conversation.service.ConversationService;
import com.example.stardust_springboot.knowledge.dto.CreateKnowledgeBaseRequest;
import com.example.stardust_springboot.knowledge.dto.SetConversationKnowledgeBasesRequest;
import com.example.stardust_springboot.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.example.stardust_springboot.knowledge.gateway.*;
import com.example.stardust_springboot.knowledge.service.*;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(KnowledgeRagIntegrationTests.FakeRagConfiguration.class)
@Transactional
class KnowledgeRagIntegrationTests {
    @Autowired KnowledgeBaseService bases;
    @Autowired ConversationKnowledgeBaseService bindingService;
    @Autowired ConversationService conversationService;
    @Autowired RagContextService ragContextService;
    @Autowired AppUserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired ChatMessageRepository messages;
    @Autowired FakeRagGateway gateway;
    @Autowired KnowledgeDocumentService documentService;
    @Autowired UserFileRepository files;
    @Autowired StorageService storage;

    @Test
    void knowledgeBaseCrudSearchAndOwnershipAreEnforced() {
        var owner = principal("kb-owner");
        var other = principal("kb-other");
        var created = bases.create(owner, new CreateKnowledgeBaseRequest("Project Atlas", "Specs"));

        assertThat(bases.list(owner, 0, 20, "atlas").items()).hasSize(1);
        assertThat(bases.get(owner, created.id()).description()).isEqualTo("Specs");
        assertThatThrownBy(() -> bases.get(other, created.id()))
                .isInstanceOf(BusinessException.class);

        bases.update(owner, created.id(), new UpdateKnowledgeBaseRequest("Atlas v2", null));
        assertThat(bases.get(owner, created.id()).name()).isEqualTo("Atlas v2");
        bases.delete(owner, created.id());
        assertThatThrownBy(() -> bases.get(owner, created.id()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void conversationBindingRejectsForeignKnowledgeAndRetrieverKeepsOwnerScope() {
        var owner = principal("rag-owner");
        var other = principal("rag-other");
        var ownBase = bases.create(owner, new CreateKnowledgeBaseRequest("Owner docs", null));
        var foreignBase = bases.create(other, new CreateKnowledgeBaseRequest("Private docs", null));
        var conversation = conversationService.create(owner, new CreateConversationRequest("RAG chat"));

        bindingService.replace(owner, conversation.id(),
                new SetConversationKnowledgeBasesRequest(List.of(ownBase.id())));
        assertThatThrownBy(() -> bindingService.replace(owner, conversation.id(),
                new SetConversationKnowledgeBasesRequest(List.of(foreignBase.id()))))
                .isInstanceOf(BusinessException.class);

        var conversationEntity = conversations.findByPublicIdAndUserIdAndDeletedAtIsNull(
                conversation.id(), owner.id()).orElseThrow();
        var user = users.findById(owner.id()).orElseThrow();
        long sequence = conversationEntity.appendMessage(Instant.now());
        var message = new ChatMessage(conversationEntity, user, MessageRole.USER,
                sequence, 0, "What is Atlas?");
        message.complete(Instant.now(), null, null);
        messages.saveAndFlush(message);

        var context = ragContextService.retrieve(message);
        // The fake source has no READY SQL document and is therefore discarded.
        assertThat(context.sources()).isEmpty();
        assertThat(gateway.lastUser.get()).isEqualTo(owner.publicId());
        assertThat(gateway.lastKnowledgeBases.get()).containsExactly(ownBase.id());
        assertThat(gateway.lastKnowledgeBases.get()).doesNotContain(foreignBase.id());
    }

    @Test
    void documentPipelinePersistsChunksSupportsFailureRetryAndProtectsOwnership() throws Exception {
        var owner = principal("doc-owner");
        var other = principal("doc-other");
        var base = bases.create(owner, new CreateKnowledgeBaseRequest("Runbooks", null));
        var user = users.findById(owner.id()).orElseThrow();
        String storageName = PublicIdGenerator.newUlid() + ".txt";
        String objectKey = owner.publicId() + "/2026/09/" + storageName;
        byte[] content = "Reset the service after checking health.".getBytes(StandardCharsets.UTF_8);
        storage.put(objectKey, new ByteArrayInputStream(content));
        var file = new UserFile(user, "runbook.txt", storageName, objectKey,
                "text/plain", "text/plain", "txt", content.length,
                "0".repeat(64), storage.providerKey(), "{}");
        file.markAvailable();
        files.saveAndFlush(file);

        var uploaded = bases.addDocument(owner, base.id(), file.getPublicId());
        gateway.failNext.set(true);
        var failed = documentService.process(owner, uploaded.id());
        assertThat(failed.status().name()).isEqualTo("FAILED");
        assertThat(failed.errorCode()).isEqualTo("PROVIDER_ERROR");
        assertThatThrownBy(() -> documentService.get(other, uploaded.id()))
                .isInstanceOf(BusinessException.class);

        var ready = documentService.retry(owner, uploaded.id());
        assertThat(ready.status().name()).isEqualTo("READY");
        assertThat(ready.chunkCount()).isEqualTo(1);
        assertThat(ready.processingVersion()).isEqualTo(2);

        var conversation = conversationService.create(owner, new CreateConversationRequest("Runbook chat"));
        bindingService.replace(owner, conversation.id(),
                new SetConversationKnowledgeBasesRequest(List.of(base.id())));
        var conversationEntity = conversations.findByPublicIdAndUserIdAndDeletedAtIsNull(
                conversation.id(), owner.id()).orElseThrow();
        var question = new ChatMessage(conversationEntity, user, MessageRole.USER,
                conversationEntity.appendMessage(Instant.now()), 0, "How do I reset it?");
        question.complete(Instant.now(), null, null);
        messages.saveAndFlush(question);
        assertThat(ragContextService.retrieve(question).sources()).hasSize(1);
    }

    private AuthenticatedUser principal(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        AppUser user = users.saveAndFlush(new AppUser(
                prefix + suffix + "@test.local", "hash", prefix));
        return new AuthenticatedUser(user.getId(), user.getPublicId(), user.getEmailNormalized(),
                user.getDisplayName(), 0, Set.of("USER"));
    }

    @TestConfiguration
    static class FakeRagConfiguration {
        @Bean
        @Primary
        FakeRagGateway fakeRagGateway() {
            return new FakeRagGateway();
        }
    }

    static class FakeRagGateway implements RagGateway {
        final AtomicReference<String> lastUser = new AtomicReference<>();
        final AtomicReference<List<String>> lastKnowledgeBases = new AtomicReference<>(List.of());
        final AtomicBoolean failNext = new AtomicBoolean();
        final AtomicReference<String> processedDocument = new AtomicReference<>();

        @Override
        public RagProcessResult process(RagProcessCommand command) {
            if (failNext.compareAndSet(true, false)) {
                throw new RagGatewayException("PROVIDER_ERROR", "simulated provider failure");
            }
            processedDocument.set(command.documentId());
            return new RagProcessResult(command.documentId(), command.knowledgeBaseId(), List.of(
                    new RagProcessResult.Chunk(0, "Atlas reference", 3, 1,
                            Map.of("filename", command.filename()))), "test-embedding");
        }

        @Override
        public RagRetrieveResult retrieve(String userPublicId, List<String> knowledgeBaseIds,
                                          String query) {
            lastUser.set(userPublicId);
            lastKnowledgeBases.set(List.copyOf(knowledgeBaseIds));
            return new RagRetrieveResult(List.of(new RagRetrieveResult.Source(
                    processedDocument.get() == null ? "missing-document" : processedDocument.get(),
                    knowledgeBaseIds.getFirst(), 0, "Atlas reference", 3,
                    0.9, 1, Map.of("query", query))));
        }

        @Override
        public void deleteDocument(String userPublicId, String knowledgeBaseId, String documentId) {
        }
    }
}
