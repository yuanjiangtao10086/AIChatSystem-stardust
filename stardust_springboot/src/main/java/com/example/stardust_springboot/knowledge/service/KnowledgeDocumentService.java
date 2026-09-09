package com.example.stardust_springboot.knowledge.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.dto.KnowledgeDocumentView;
import com.example.stardust_springboot.knowledge.entity.DocumentChunk;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocument;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;
import com.example.stardust_springboot.knowledge.gateway.*;
import com.example.stardust_springboot.knowledge.repository.DocumentChunkRepository;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final StorageService storage;
    private final RagGateway gateway;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public KnowledgeDocumentService(KnowledgeDocumentRepository documents,
                                    DocumentChunkRepository chunks,
                                    StorageService storage,
                                    RagGateway gateway,
                                    ObjectMapper mapper,
                                    PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.gateway = gateway;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public KnowledgeDocumentView process(AuthenticatedUser principal, String documentId) {
        ProcessTarget target = transactions.execute(status -> {
            KnowledgeDocument document = require(principal.id(), documentId);
            if (document.getStatus() != KnowledgeDocumentStatus.UPLOADED) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            document.beginParsing();
            return ProcessTarget.from(document);
        });
        if (target == null) throw new BusinessException(ErrorCode.PERSISTENCE_ERROR);

        try (InputStream input = storage.open(target.objectKey())) {
            RagProcessResult result = gateway.process(new RagProcessCommand(
                    target.userPublicId(), target.knowledgeBaseId(), target.documentId(),
                    target.filename(), target.mimeType(), input));
            transactions.executeWithoutResult(status -> require(principal.id(), documentId)
                    .markParsed("plain-text"));
            transactions.executeWithoutResult(status -> require(principal.id(), documentId)
                    .beginEmbedding(target.providerKey(), result.embeddingModel()));
            transactions.executeWithoutResult(status -> complete(principal.id(), documentId, result));
        } catch (Exception error) {
            String code = error instanceof RagGatewayException gatewayError
                    ? gatewayError.getCode() : "DOCUMENT_PROCESSING_FAILED";
            transactions.executeWithoutResult(status ->
                    require(principal.id(), documentId).markFailed(code, "document processing failed"));
        }
        return get(principal, documentId);
    }

    public KnowledgeDocumentView retry(AuthenticatedUser principal, String documentId) {
        transactions.executeWithoutResult(status -> {
            KnowledgeDocument document = require(principal.id(), documentId);
            if (document.getStatus() != KnowledgeDocumentStatus.FAILED) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            document.prepareRetry();
        });
        return process(principal, documentId);
    }

    public void delete(AuthenticatedUser principal, String documentId) {
        DeleteTarget target = transactions.execute(status -> {
            KnowledgeDocument document = require(principal.id(), documentId);
            chunks.deleteByDocumentId(document.getId());
            document.softDelete();
            return new DeleteTarget(document.getUser().getPublicId(),
                    document.getKnowledgeBase().getPublicId(), document.getPublicId());
        });
        if (target != null) {
            try {
                gateway.deleteDocument(target.userId(), target.knowledgeBaseId(), target.documentId());
            } catch (RuntimeException ignored) {
                // Vector cleanup is idempotent and may be retried by a future reconciliation job.
            }
        }
    }

    public KnowledgeDocumentView removeVectors(AuthenticatedUser principal, String documentId) {
        DeleteTarget target = transactions.execute(status -> {
            KnowledgeDocument document = require(principal.id(), documentId);
            chunks.deleteByDocumentId(document.getId());
            document.markFailed("VECTOR_REMOVED", "vector index removed by administrator");
            return new DeleteTarget(document.getUser().getPublicId(),
                    document.getKnowledgeBase().getPublicId(), document.getPublicId());
        });
        if (target != null) gateway.deleteDocument(target.userId(), target.knowledgeBaseId(), target.documentId());
        return get(principal, documentId);
    }

    public KnowledgeDocumentView get(AuthenticatedUser principal, String documentId) {
        KnowledgeDocumentView result = transactions.execute(status ->
                KnowledgeDocumentView.from(require(principal.id(), documentId)));
        if (result == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return result;
    }

    private void complete(Long userId, String documentId, RagProcessResult result) {
        KnowledgeDocument document = require(userId, documentId);
        if (!document.getPublicId().equals(result.documentId())
                || !document.getKnowledgeBase().getPublicId().equals(result.knowledgeBaseId())
                || result.chunks().isEmpty()) {
            throw new RagGatewayException("RAG_PROTOCOL_ERROR", "RAG response identity is invalid");
        }
        var indexes = result.chunks().stream().map(RagProcessResult.Chunk::chunkIndex).toList();
        if (indexes.stream().anyMatch(index -> index < 0)
                || new java.util.HashSet<>(indexes).size() != indexes.size()
                || result.chunks().stream().anyMatch(chunk -> chunk.content() == null
                        || chunk.content().isBlank() || chunk.tokenCount() <= 0)) {
            throw new RagGatewayException("RAG_PROTOCOL_ERROR", "RAG response chunks are invalid");
        }
        chunks.deleteByDocumentId(document.getId());
        var entities = result.chunks().stream().map(chunk -> new DocumentChunk(
                document, chunk.chunkIndex(), chunk.content(), chunk.tokenCount(), chunk.page(),
                json(chunk.sourceMetadata()), sha256(chunk.content()))).toList();
        chunks.saveAll(entities);
        document.markReady(entities.size());
    }

    private KnowledgeDocument require(Long userId, String id) {
        return documents.findByPublicIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (Exception error) {
            throw new RagGatewayException("RAG_PROTOCOL_ERROR", "invalid chunk metadata", error);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record ProcessTarget(String userPublicId, String knowledgeBaseId, String documentId,
                                 String filename, String mimeType, String objectKey,
                                 String providerKey) {
        static ProcessTarget from(KnowledgeDocument document) {
            return new ProcessTarget(document.getUser().getPublicId(),
                    document.getKnowledgeBase().getPublicId(), document.getPublicId(),
                    document.getUserFile().getStorageName(),
                    document.getUserFile().getDetectedMime(),
                    document.getUserFile().getObjectKey(), "openai-compatible");
        }
    }

    private record DeleteTarget(String userId, String knowledgeBaseId, String documentId) {
    }
}
