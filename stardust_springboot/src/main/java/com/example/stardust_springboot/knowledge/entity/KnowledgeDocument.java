package com.example.stardust_springboot.knowledge.entity;

import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument extends SoftDeleteEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false, updatable = false)
    private KnowledgeBase knowledgeBase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_file_id", nullable = false, updatable = false)
    private UserFile userFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeDocumentStatus status = KnowledgeDocumentStatus.UPLOADED;

    @Column(name = "parser_type", length = 32)
    private String parserType;
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;
    @Column(name = "processing_version", nullable = false)
    private int processingVersion = 1;
    @Column(name = "embedding_provider", length = 64)
    private String embeddingProvider;
    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(KnowledgeBase knowledgeBase, AppUser user, UserFile userFile) {
        this.knowledgeBase = knowledgeBase;
        this.user = user;
        this.userFile = userFile;
    }

    public void beginParsing() {
        status = KnowledgeDocumentStatus.PARSING;
        startedAt = Instant.now();
        completedAt = null;
        errorCode = null;
        errorMessage = null;
        chunkCount = 0;
    }

    public void markParsed(String parserType) {
        status = KnowledgeDocumentStatus.PARSED;
        this.parserType = parserType;
    }

    public void beginEmbedding(String provider, String model) {
        status = KnowledgeDocumentStatus.EMBEDDING;
        embeddingProvider = provider;
        embeddingModel = model;
    }

    public void markReady(int chunks) {
        status = KnowledgeDocumentStatus.READY;
        chunkCount = chunks;
        completedAt = Instant.now();
    }

    public void markFailed(String code, String message) {
        status = KnowledgeDocumentStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
    }

    public void prepareRetry() {
        if (status != KnowledgeDocumentStatus.FAILED) {
            throw new IllegalStateException("only failed documents can be retried");
        }
        processingVersion++;
        status = KnowledgeDocumentStatus.UPLOADED;
        errorCode = null;
        errorMessage = null;
        completedAt = null;
    }

    /** Keeps {@code chunk_count} aligned with the actual chunk rows after a vector wipe. */
    public void clearChunks() {
        chunkCount = 0;
    }

    public void softDelete() {
        markDeletedAt(Instant.now());
    }

    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public AppUser getUser() { return user; }
    public UserFile getUserFile() { return userFile; }
    public KnowledgeDocumentStatus getStatus() { return status; }
    public String getParserType() { return parserType; }
    public int getChunkCount() { return chunkCount; }
    public int getProcessingVersion() { return processingVersion; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public String getEmbeddingModel() { return embeddingModel; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
