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

    public void attach(ChatMessage message, Long userId, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return;
        LinkedHashSet<String> ids = new LinkedHashSet<>(requestedIds);
        if (ids.size() != requestedIds.size() || ids.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<UserFile> files = fileRepository.findByPublicIdInAndUserIdAndStatusAndDeletedAtIsNull(
                ids, userId, UserFileStatus.AVAILABLE);
        if (files.size() != ids.size()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<String, UserFile> byId = files.stream().collect(Collectors.toMap(UserFile::getPublicId,
                Function.identity()));
        int order = 0;
        for (String id : ids) {
            attachmentRepository.save(new ChatMessageAttachment(
                    message, byId.get(id), message.getUser(), order++));
        }
        attachmentRepository.flush();
    }
}
