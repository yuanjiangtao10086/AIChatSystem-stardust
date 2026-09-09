package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.request.AiRequestLog;
import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import com.example.stardust_springboot.conversation.entity.FinishReason;
import com.example.stardust_springboot.conversation.entity.MessageContentFormat;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.entity.MessageStatus;
import com.example.stardust_springboot.conversation.memory.ConversationContextBuilder;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.conversation.service.MessageAttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AiStreamPersistenceService {
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiModelRepository modelRepository;
    private final AiRequestLogRepository requestLogRepository;
    private final MessageAttachmentService attachmentService;
    private final ConversationContextBuilder contextBuilder;
    private final Clock clock;

    public AiStreamPersistenceService(ConversationRepository conversationRepository,
                                      ChatMessageRepository messageRepository,
                                      AiModelRepository modelRepository,
                                      AiRequestLogRepository requestLogRepository,
                                      MessageAttachmentService attachmentService,
                                      ConversationContextBuilder contextBuilder,
                                      Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.modelRepository = modelRepository;
        this.requestLogRepository = requestLogRepository;
        this.attachmentService = attachmentService;
        this.contextBuilder = contextBuilder;
        this.clock = clock;
    }

    @Transactional
    public PreparedAiStream prepare(AuthenticatedUser principal, String conversationPublicId,
                                    String idempotencyKey, StreamChatRequest request) {
        Conversation conversation = conversationRepository
                .findOwnedForUpdate(conversationPublicId, principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        requireUnusedIdempotencyKey(principal.id(), idempotencyKey);
        AiModel model = requireModel(request.modelId());

        ChatMessage parent = null;
        if (request.parentMessageId() != null && !request.parentMessageId().isBlank()) {
            parent = messageRepository.findByPublicIdAndConversationIdAndUserIdAndDeletedAtIsNull(
                            request.parentMessageId(), conversation.getId(), principal.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        }

        Instant now = clock.instant();
        MessageContentFormat format = request.contentType() == null
                ? MessageContentFormat.PLAIN_TEXT : request.contentType();
        ChatMessage userMessage = new ChatMessage(conversation, conversation.getUser(), MessageRole.USER,
                conversation.appendMessage(now), 0, request.content().trim());
        userMessage.setParentMessage(parent);
        userMessage.setContentFormat(format);
        userMessage.setClientRequestId(idempotencyKey);
        userMessage.complete(now, null, null);
        messageRepository.saveAndFlush(userMessage);
        attachmentService.attach(userMessage, principal.id(), request.attachmentIds());

        ChatMessage assistantMessage = new ChatMessage(
                conversation, conversation.getUser(), MessageRole.ASSISTANT,
                conversation.appendMessage(now), 0, "");
        assistantMessage.setParentMessage(userMessage);
        assistantMessage.setContentFormat(MessageContentFormat.MARKDOWN);
        assistantMessage.setModel(model);
        messageRepository.saveAndFlush(assistantMessage);

        String requestId = PublicIdGenerator.newUlid();
        AiRequestLog requestLog = requestLogRepository.saveAndFlush(
                new AiRequestLog(requestId, conversation.getUser(), conversation, assistantMessage, model));

        List<AiGatewayRequest.AiGatewayMessage> context = contextBuilder.build(userMessage, model);

        return new PreparedAiStream(requestId, principal.id(), conversation.getId(),
                assistantMessage.getId(), requestLog.getId(), conversation.getPublicId(),
                userMessage.getPublicId(), assistantMessage.getPublicId(), model.getPublicId(),
                model.getProvider().getCode(), model.getExternalModelId(), "SEND", context);
    }

    @Transactional
    public PreparedAiStream prepareRegenerate(AuthenticatedUser principal, String messagePublicId,
                                               String idempotencyKey, RegenerateMessageRequest request) {
        requireUnusedIdempotencyKey(principal.id(), idempotencyKey);
        ChatMessage target = requireOwnedMessage(messagePublicId, principal.id());
        Conversation conversation = requireActiveOwnedConversation(target, principal.id());
        if (target.getRole() != MessageRole.ASSISTANT
                || target.getParentMessage() == null
                || target.getParentMessage().getRole() != MessageRole.USER
                || !isTerminal(target.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AiModel model = requireModel(request.modelId());
        Instant now = clock.instant();
        int variant = nextVariant(conversation.getId(), target.getSequenceNo());
        ChatMessage assistant = new ChatMessage(conversation, conversation.getUser(), MessageRole.ASSISTANT,
                target.getSequenceNo(), variant, "");
        assistant.setParentMessage(target.getParentMessage());
        assistant.setSupersedesMessage(target);
        assistant.setContentFormat(MessageContentFormat.MARKDOWN);
        assistant.setModel(model);
        assistant.setClientRequestId(idempotencyKey);
        conversation.appendVariant(now);
        messageRepository.saveAndFlush(assistant);
        AiRequestLog log = requestLogRepository.saveAndFlush(
                new AiRequestLog(PublicIdGenerator.newUlid(), conversation.getUser(), conversation, assistant, model));
        return prepared(principal, conversation, target.getParentMessage(), assistant, log, model,
                "REGENERATE", contextBuilder.build(target.getParentMessage(), model));
    }

    @Transactional
    public PreparedAiStream prepareEditAndResend(AuthenticatedUser principal, String messagePublicId,
                                                  String idempotencyKey, EditAndResendMessageRequest request) {
        requireUnusedIdempotencyKey(principal.id(), idempotencyKey);
        ChatMessage target = requireOwnedMessage(messagePublicId, principal.id());
        Conversation conversation = requireActiveOwnedConversation(target, principal.id());
        if (target.getRole() != MessageRole.USER || target.getStatus() != MessageStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AiModel model = requireModel(request.modelId());
        Instant now = clock.instant();
        MessageContentFormat format = request.contentType() == null
                ? MessageContentFormat.PLAIN_TEXT : request.contentType();

        ChatMessage revisedUser = new ChatMessage(conversation, conversation.getUser(), MessageRole.USER,
                target.getSequenceNo(), nextVariant(conversation.getId(), target.getSequenceNo()),
                request.content().trim());
        revisedUser.setParentMessage(target.getParentMessage());
        revisedUser.setSupersedesMessage(target);
        revisedUser.setContentFormat(format);
        revisedUser.setClientRequestId(idempotencyKey);
        revisedUser.complete(now, null, null);
        conversation.appendVariant(now);
        messageRepository.saveAndFlush(revisedUser);
        attachmentService.attach(revisedUser, principal.id(), request.attachmentIds());

        long assistantSequence = target.getSequenceNo() + 1;
        ChatMessage supersededAssistant = messageRepository
                .findFirstByConversationIdAndUserIdAndSequenceNoAndRoleAndDeletedAtIsNullOrderByVariantNoDescIdDesc(
                        conversation.getId(), principal.id(), assistantSequence, MessageRole.ASSISTANT)
                .orElse(null);
        ChatMessage assistant = new ChatMessage(conversation, conversation.getUser(), MessageRole.ASSISTANT,
                assistantSequence, nextVariant(conversation.getId(), assistantSequence), "");
        assistant.setParentMessage(revisedUser);
        assistant.setSupersedesMessage(supersededAssistant);
        assistant.setContentFormat(MessageContentFormat.MARKDOWN);
        assistant.setModel(model);
        conversation.appendVariant(now);
        messageRepository.saveAndFlush(assistant);

        AiRequestLog log = requestLogRepository.saveAndFlush(
                new AiRequestLog(PublicIdGenerator.newUlid(), conversation.getUser(), conversation, assistant, model));
        return prepared(principal, conversation, revisedUser, assistant, log, model,
                "EDIT_AND_RESEND", contextBuilder.build(revisedUser, model));
    }

    @Transactional
    public void markStreaming(PreparedAiStream stream) {
        Instant now = clock.instant();
        requireMessage(stream).startStreaming(now);
        requireLog(stream).start(now);
    }

    @Transactional
    public void complete(PreparedAiStream stream, String content, FinishReason reason, TokenTotals usage) {
        Instant now = clock.instant();
        ChatMessage message = requireMessage(stream);
        message.completeStreaming(content, now, reason, usage.promptTokens(),
                usage.completionTokens(), usage.totalTokens());
        requireLog(stream).complete(now, usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
        requireConversation(stream).touchLastMessageAt(now);
    }

    @Transactional
    public void stop(PreparedAiStream stream, String content) {
        Instant now = clock.instant();
        requireMessage(stream).stopStreaming(content, now);
        requireLog(stream).stop(now);
        requireConversation(stream).touchLastMessageAt(now);
    }

    @Transactional
    public void fail(PreparedAiStream stream, String content, String code) {
        Instant now = clock.instant();
        requireMessage(stream).failStreaming(content, now, code, "AI generation failed");
        requireLog(stream).fail(now, code);
        requireConversation(stream).touchLastMessageAt(now);
    }

    @Transactional(readOnly = true)
    public AiRequestStatus requireRequestStatus(String requestId, Long userId) {
        return requestLogRepository.findByRequestIdAndUserId(requestId, userId)
                .map(AiRequestLog::getStatus)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireUnusedIdempotencyKey(Long userId, String idempotencyKey) {
        if (messageRepository.findByUserIdAndClientRequestIdAndDeletedAtIsNull(userId, idempotencyKey).isPresent()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private AiModel requireModel(String modelId) {
        return modelRepository.findEnabledChatModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ChatMessage requireOwnedMessage(String publicId, Long userId) {
        return messageRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(publicId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Conversation requireActiveOwnedConversation(ChatMessage message, Long userId) {
        Conversation conversation = conversationRepository
                .findOwnedForUpdate(message.getConversation().getPublicId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return conversation;
    }

    private int nextVariant(Long conversationId, long sequenceNo) {
        return messageRepository.findMaxVariantNo(conversationId, sequenceNo) + 1;
    }

    private boolean isTerminal(MessageStatus status) {
        return status == MessageStatus.COMPLETED
                || status == MessageStatus.STOPPED
                || status == MessageStatus.FAILED;
    }

    private PreparedAiStream prepared(AuthenticatedUser principal, Conversation conversation,
                                      ChatMessage userMessage, ChatMessage assistantMessage,
                                      AiRequestLog requestLog, AiModel model, String operation,
                                      List<AiGatewayRequest.AiGatewayMessage> context) {
        return new PreparedAiStream(requestLog.getRequestId(), principal.id(), conversation.getId(),
                assistantMessage.getId(), requestLog.getId(), conversation.getPublicId(),
                userMessage.getPublicId(), assistantMessage.getPublicId(), model.getPublicId(),
                model.getProvider().getCode(), model.getExternalModelId(), operation, context);
    }

    private ChatMessage requireMessage(PreparedAiStream stream) {
        return messageRepository.findById(stream.assistantMessageDatabaseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AiRequestLog requireLog(PreparedAiStream stream) {
        return requestLogRepository.findById(stream.requestLogDatabaseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Conversation requireConversation(PreparedAiStream stream) {
        return conversationRepository.findById(stream.conversationDatabaseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public record TokenTotals(Long promptTokens, Long completionTokens, Long totalTokens) {
        public static TokenTotals empty() {
            return new TokenTotals(null, null, null);
        }
    }
}
