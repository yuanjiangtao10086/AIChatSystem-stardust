package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.entity.AttachmentType;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.ChatMessageAttachment;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.conversation.repository.ChatMessageAttachmentRepository;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.service.FileTypePolicy;
import com.example.stardust_springboot.file.service.ValidatedUpload;
import com.example.stardust_springboot.file.storage.StorageService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Turns a streamed AI artifact into a durable user file.
 *
 * The Python service owns generation; this service owns storage and the DB rows, keeping the
 * "storage belongs to Spring Boot" boundary (ADR-003). Validation re-sniffs the bytes and the
 * storage quota is reserved before the file row is created AVAILABLE. A storage write failure
 * marks the row FAILED and releases the quota so no orphaned-but-available file is left behind.
 */
@Service
public class MessageArtifactService {
    private final FileTypePolicy fileTypePolicy;
    private final FilePersistenceService filePersistenceService;
    private final StorageService storageService;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;

    public MessageArtifactService(FileTypePolicy fileTypePolicy, FilePersistenceService filePersistenceService,
                                  StorageService storageService, ChatMessageRepository messageRepository,
                                  ChatMessageAttachmentRepository attachmentRepository) {
        this.fileTypePolicy = fileTypePolicy;
        this.filePersistenceService = filePersistenceService;
        this.storageService = storageService;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public ArtifactResult persist(PreparedAiStream stream, long userId, ArtifactBuffer buffer) {
        byte[] content = buffer.bytes();
        ValidatedUpload upload = fileTypePolicy.validateGenerated(buffer.filename(), buffer.mimeType(), content);
        UserFile file = filePersistenceService.persistGenerated(userId, upload, storageService.providerKey());
        try {
            storageService.put(file.getObjectKey(), new ByteArrayInputStream(content));
        } catch (Exception error) {
            // StorageService may reject malformed keys with IllegalArgumentException (not just
            // IOException); any failure must mark the row FAILED or an AVAILABLE row without
            // bytes would linger and break downloads later.
            filePersistenceService.failGenerated(userId, file.getPublicId());
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
        ChatMessage message = messageRepository.findById(stream.assistantMessageDatabaseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        attachmentRepository.save(new ChatMessageAttachment(message, file, file.getUser(), 0, AttachmentType.OUTPUT));
        return new ArtifactResult(
                file.getPublicId(),
                file.getOriginalName(),
                file.getDetectedMime(),
                file.getSizeBytes(),
                "/api/v1/files/" + file.getPublicId() + "/download");
    }
}
