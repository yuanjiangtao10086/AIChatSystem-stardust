package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.conversation.dto.MessageAttachmentView;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.conversation.repository.ChatMessageAttachmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;

    public MessageService(ConversationRepository conversationRepository,
                          ChatMessageRepository messageRepository,
                          ChatMessageAttachmentRepository attachmentRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<MessageView> list(AuthenticatedUser principal, String conversationId,
                                        int page, int size) {
        Conversation conversation = requireOwned(conversationId, principal.id());
        Page<ChatMessage> messages = messageRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(
                        conversation.getId(), principal.id(),
                        PageRequest.of(page, size, Sort.by("sequenceNo").ascending()
                                .and(Sort.by("id").ascending())));
        Map<Long, List<MessageAttachmentView>> attachments = messages.isEmpty() ? Map.of()
                : attachmentRepository.findForMessages(messages.map(ChatMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getMessage().getId(),
                        Collectors.mapping(MessageAttachmentView::from, Collectors.toList())));
        Page<MessageView> result = messages.map(message -> MessageView.from(
                message, attachments.getOrDefault(message.getId(), List.of())));
        return PageResult.from(result);
    }

    private Conversation requireOwned(String conversationId, Long userId) {
        return conversationRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
