package com.example.stardust_springboot.knowledge.entity;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "document_chunk")
public class DocumentChunk extends PublicIdEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_document_id", nullable = false, updatable = false)
    private KnowledgeDocument document;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false, updatable = false)
    private KnowledgeBase knowledgeBase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;
    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "token_count", nullable = false)
    private int tokenCount;
    @Column(name = "page_number")
    private Integer page;
    @Column(name = "source_metadata_json", columnDefinition = "TEXT")
    private String sourceMetadataJson;
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;
    @Column(name = "processing_version", nullable = false)
    private int processingVersion;

    protected DocumentChunk() {
    }

    public DocumentChunk(KnowledgeDocument document, int chunkIndex, String content,
                         int tokenCount, Integer page, String sourceMetadataJson,
                         String contentHash) {
        this.document = document;
        this.knowledgeBase = document.getKnowledgeBase();
        this.user = document.getUser();
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.page = page;
        this.sourceMetadataJson = sourceMetadataJson;
        this.contentHash = contentHash;
        this.processingVersion = document.getProcessingVersion();
    }

    public KnowledgeDocument getDocument() { return document; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public int getTokenCount() { return tokenCount; }
    public Integer getPage() { return page; }
    public String getSourceMetadataJson() { return sourceMetadataJson; }
    public int getProcessingVersion() { return processingVersion; }
}
