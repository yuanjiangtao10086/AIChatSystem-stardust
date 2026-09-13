package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.ChatMessageAttachment;
import com.example.stardust_springboot.conversation.repository.ChatMessageAttachmentRepository;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessageAttachmentService {
    private static final int MAX_ATTACHMENTS = 10;
    private final UserFileRepository fileRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;

    public MessageAttachmentService(UserFileRepository fileRepository,
                                    ChatMessageAttachmentRepository attachmentRepository) {
        this.fileRepository = fileRepository;
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Links already-uploaded files to a message and returns their total size in bytes.
     *
     * <p>The total size lets the caller reserve quota for attachment content without reading any file
     * on the HTTP request thread. Files are never re-uploaded: only an owner-scoped reference to an
     * existing {@code user_file} row is created.
     *
     * @return total size of the attached files, or {@code 0} when nothing was attached
     */
    public long attach(ChatMessage message, Long userId, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return 0;
        LinkedHashSet<String> ids = new LinkedHashSet<>(requestedIds);
        if (ids.size() != requestedIds.size() || ids.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<UserFile> files = fileRepository.findByPublicIdInAndUserIdAndStatusAndDeletedAtIsNull(
                ids, userId, UserFileStatus.AVAILABLE);
        if (files.size() != ids.size()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<String, UserFile> byId = files.stream().collect(Collectors.toMap(UserFile::getPublicId,
                Function.identity()));
        long totalBytes = 0;
        int order = 0;
        for (String id : ids) {
            UserFile file = byId.get(id);
            totalBytes += file.getSizeBytes();
            attachmentRepository.save(new ChatMessageAttachment(
                    message, file, message.getUser(), order++));
        }
        attachmentRepository.flush();
        return totalBytes;
    }

    /**
     * Total size of the files already attached to a message. Used when a regenerated answer has to
     * reserve quota for the attachments of the original user message without reading them again.
     */
    public long attachmentBytes(Long messageId) {
        return attachmentRepository.findByMessage(messageId).stream()
                .mapToLong(link -> link.getUserFile().getSizeBytes())
                .sum();
    }
}
