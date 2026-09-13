package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.dto.ConversationExportFormat;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an owned conversation as a downloadable Markdown or JSON document.
 *
 * <p>The service is read-only and owner-scoped: it never touches storage, never reads other users' rows and
 * never mutates the conversation. Only the messages of the currently active branch are exported, i.e. the
 * highest variant per sequence number, which is exactly what the chat UI renders.
 */
@Service
public class ConversationExportService {

    private static final int MAX_MESSAGES = 5000;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ConversationExportService(ConversationRepository conversationRepository,
                                     ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public ConversationExport export(AuthenticatedUser principal, String conversationId,
                                     ConversationExportFormat format) {
        Conversation conversation = conversationRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(conversationId, principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        List<ChatMessage> messages = activeBranch(conversation.getId());
        String body = format == ConversationExportFormat.JSON
                ? renderJson(conversation, messages)
                : renderMarkdown(conversation, messages);
        return new ConversationExport(
                fileName(conversation.getTitle(), format),
                format.contentType(),
                body);
    }

    private List<ChatMessage> activeBranch(Long conversationId) {
        List<ChatMessage> ordered = messageRepository.findByConversationIdAndDeletedAtIsNull(
                        conversationId,
                        PageRequest.of(0, MAX_MESSAGES, Sort.by("sequenceNo").ascending()
                                .and(Sort.by("id").ascending())))
                .getContent();
        Map<Long, ChatMessage> latest = new LinkedHashMap<>();
        for (ChatMessage message : ordered) {
            ChatMessage current = latest.get(message.getSequenceNo());
            if (current == null || message.getVariantNo() >= current.getVariantNo()) {
                latest.put(message.getSequenceNo(), message);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private String renderMarkdown(Conversation conversation, List<ChatMessage> messages) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(conversation.getTitle()).append("\n\n");
        out.append("- 会话 ID：").append(conversation.getPublicId()).append('\n');
        out.append("- 导出时间（UTC）：").append(STAMP.format(Instant.now())).append('\n');
        out.append("- 消息数量：").append(messages.size()).append("\n\n---\n\n");
        for (ChatMessage message : messages) {
            out.append("### ").append(MessageRole.USER == message.getRole() ? "我" : "星语 AI");
            out.append(" · ").append(STAMP.format(message.getCreatedAt())).append(" UTC\n\n");
            String content = message.getContentText();
            out.append(content == null || content.isBlank() ? "_(空内容)_" : content);
            out.append("\n\n");
            if (message.getRole() != MessageRole.USER) {
                List<String> meta = new ArrayList<>();
                meta.add("状态 " + message.getStatus());
                if (message.getModel() != null) {
                    meta.add("模型 " + message.getModel().getPublicId());
                }
                if (message.getTotalTokens() != null) {
                    meta.add("tokens " + message.getTotalTokens());
                }
                out.append("> ").append(String.join(" · ", meta)).append("\n\n");
            }
        }
        return out.toString();
    }

    /**
     * Builds the JSON document by hand instead of via Jackson so the export never depends on a specific
     * Jackson major version (Spring Boot 4 ships Jackson 3 under the {@code tools.jackson} package). The
     * structure is fixed and small, so hand-rolling the serializer keeps the dependency surface flat and
     * the escaping correct. The body is pretty-printed because a conversation export is something a human
     * will also read and diff.
     */
    private String renderJson(Conversation conversation, List<ChatMessage> messages) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"conversationId\": ").append(jsonString(conversation.getPublicId())).append(",\n");
        out.append("  \"title\": ").append(jsonString(conversation.getTitle())).append(",\n");
        out.append("  \"status\": ").append(jsonString(conversation.getStatus().name())).append(",\n");
        out.append("  \"exportedAt\": ").append(jsonString(Instant.now().toString())).append(",\n");
        out.append("  \"timeZone\": ").append(jsonString("UTC")).append(",\n");
        out.append("  \"messages\": [\n");
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            out.append("    {\n");
            out.append("      \"id\": ").append(jsonString(message.getPublicId())).append(",\n");
            out.append("      \"role\": ").append(jsonString(message.getRole().name())).append(",\n");
            out.append("      \"sequenceNo\": ").append(message.getSequenceNo()).append(",\n");
            out.append("      \"variantNo\": ").append(message.getVariantNo()).append(",\n");
            out.append("      \"status\": ").append(jsonString(message.getStatus().name())).append(",\n");
            out.append("      \"content\": ").append(jsonString(message.getContentText())).append(",\n");
            out.append("      \"contentType\": ")
                    .append(jsonString(message.getContentFormat().name()))
                    .append(",\n");
            out.append("      \"modelId\": ")
                    .append(message.getModel() == null ? "null" : jsonString(message.getModel().getPublicId()))
                    .append(",\n");
            out.append("      \"promptTokens\": ").append(orNull(message.getPromptTokens())).append(",\n");
            out.append("      \"completionTokens\": ").append(orNull(message.getCompletionTokens())).append(",\n");
            out.append("      \"totalTokens\": ").append(orNull(message.getTotalTokens())).append(",\n");
            out.append("      \"createdAt\": ")
                    .append(message.getCreatedAt() == null ? "null" : jsonString(message.getCreatedAt().toString()))
                    .append("\n");
            out.append("    }").append(index < messages.size() - 1 ? ",\n" : "\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    /** Emits a JSON string literal with every required escape (quotes, backslash, control chars, unicode). */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private static String orNull(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String fileName(String title, ConversationExportFormat format) {
        String safe = title == null ? "conversation" : title.replaceAll("[^\\p{L}\\p{N}_-]+", "_").trim();
        if (safe.isEmpty()) {
            safe = "conversation";
        }
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return safe + "." + format.extension();
    }

    /**
     * Result of a successful export: the value the controller turns into a download response.
     */
    public record ConversationExport(String fileName, String contentType, String body) {
    }
}
