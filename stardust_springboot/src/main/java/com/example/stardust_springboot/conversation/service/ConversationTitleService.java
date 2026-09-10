package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.gateway.AiGateway;
import com.example.stardust_springboot.ai.gateway.AiGatewayEvent;
import com.example.stardust_springboot.ai.gateway.AiGatewayException;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest.AiGatewayMessage;
import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.conversation.dto.ConversationView;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates a short, human-friendly conversation title from the user's first message. Title creation is
 * intentionally decoupled from the chat stream: it is invoked by a dedicated endpoint after the first
 * message has been persisted, so a slow or failing model can never block or break the actual reply.
 *
 * <p>Behaviour is idempotent and safe:
 * <ul>
 *   <li>only runs when the current title is still a default placeholder;</li>
 *   <li>only runs when exactly one user message exists (i.e. the first message);</li>
 *   <li>falls back to a truncated first message if the AI call fails, times out, or returns junk;</li>
 *   <li>never overwrites a title the user has already customised.</li>
 * </ul>
 */
@Service
public class ConversationTitleService {

    private static final Logger log = LoggerFactory.getLogger(ConversationTitleService.class);

    private static final Set<String> DEFAULT_TITLES = Set.of(
            "new conversation", "new chat", "新对话", "new", "chat", "conversation", "对话", "");
    private static final int MAX_TITLE = 24;

    private static final Set<String> PREFIXES = Set.of(
            "标题：", "标题:", "聊天标题：", "聊天标题:",
            "用户询问", "关于", "聊天", "问题", "对话", "请问", "帮我", "请帮我", "麻烦帮我");

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiModelRepository modelRepository;
    private final AiGateway gateway;

    public ConversationTitleService(ConversationRepository conversationRepository,
                                    ChatMessageRepository messageRepository,
                                    AiModelRepository modelRepository,
                                    AiGateway gateway) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.modelRepository = modelRepository;
        this.gateway = gateway;
    }

    @Transactional
    public ConversationView generate(AuthenticatedUser principal, String conversationId) {
        Conversation conversation = conversationRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(conversationId, principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // Never overwrite a title the user has already customised.
        if (!isDefaultTitle(conversation.getTitle())) {
            return ConversationView.from(conversation);
        }
        ChatMessage firstUser = messageRepository
                .findFirstByConversationIdAndUserIdAndRoleAndDeletedAtIsNullOrderBySequenceNoAscIdAsc(
                        conversation.getId(), principal.id(), MessageRole.USER)
                .orElse(null);
        if (firstUser == null) {
            return ConversationView.from(conversation);
        }
        long userMessageCount = messageRepository
                .countByConversationIdAndRoleAndDeletedAtIsNull(conversation.getId(), MessageRole.USER);
        // Only the first user message may trigger auto-naming; later messages must not rename.
        if (userMessageCount != 1) {
            return ConversationView.from(conversation);
        }
        String firstText = firstUser.getContentText();
        String title = generateFromAi(conversation, firstText);
        if (title == null || title.isBlank()) {
            title = fallbackTitle(firstText);
        }
        conversation.setTitle(title);
        conversationRepository.saveAndFlush(conversation);
        return ConversationView.from(conversation);
    }

    private String generateFromAi(Conversation conversation, String firstUserMessage) {
        try {
            AiModel model = conversation.getDefaultModel();
            if (model == null) {
                model = modelRepository.findEnabledChatModels().stream().findFirst().orElse(null);
            }
            if (model == null) {
                return null;
            }
            AiGatewayRequest request = new AiGatewayRequest(
                    PublicIdGenerator.newUlid(),
                    model.getProvider().getCode(),
                    model.getExternalModelId(),
                    List.of(new AiGatewayMessage("user", buildPrompt(firstUserMessage))));
            StringBuilder content = new StringBuilder();
            gateway.stream(request, new StreamCancellation(), event -> {
                if ("delta".equals(event.type())) {
                    Object value = event.payload().get("content");
                    if (value instanceof String text) {
                        content.append(text);
                    }
                }
            });
            return cleanAiTitle(content.toString());
        } catch (AiGatewayException error) {
            log.warn("Title generation AI call failed conversationId={} code={}",
                    conversation.getPublicId(), error.code());
            return null;
        } catch (Exception error) {
            log.warn("Title generation failed conversationId={}", conversation.getPublicId(), error);
            return null;
        }
    }

    private String buildPrompt(String firstUserMessage) {
        return "请根据用户第一次发送的消息生成一个简洁的聊天标题。\n"
                + "要求：\n"
                + "1.准确概括核心内容；\n"
                + "2.中文优先；\n"
                + "3.6-18个中文字左右；\n"
                + "4.最长不超过24个字符；\n"
                + "5.不要使用引号；\n"
                + "6.不要使用句号；\n"
                + "7.不要使用“用户询问”、“关于”、“聊天”、“问题”、“请问”、“帮我”等无意义前缀；\n"
                + "8.只返回标题，不要返回任何解释。\n\n"
                + "用户消息：\n"
                + firstUserMessage;
    }

    private String cleanAiTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String text = normalizeWhitespace(raw);
        text = stripWrappingQuotes(text);
        text = stripTrailingPunctuation(text);
        text = stripPrefixes(text);
        text = capLength(text);
        return text.isBlank() ? null : text;
    }

    private String fallbackTitle(String firstUserMessage) {
        String text = normalizeWhitespace(firstUserMessage);
        text = stripPrefixes(text,
                "你好", "您好", "请问", "帮我", "请帮我", "麻烦帮我", "麻烦",
                "hi", "hello", "hey", "在吗", "在么");
        if (text.isBlank()) {
            return "新对话";
        }
        return capLength(text);
    }

    private boolean isDefaultTitle(String title) {
        if (title == null) {
            return true;
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        return DEFAULT_TITLES.contains(normalized) || normalized.isEmpty();
    }

    private String normalizeWhitespace(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripWrappingQuotes(String value) {
        String text = value;
        while (true) {
            if (text.length() >= 2) {
                char first = text.charAt(0);
                char last = text.charAt(text.length() - 1);
                if ((first == '"' && last == '"')
                        || (first == '\'' && last == '\'')
                        || (first == '“' && last == '”')
                        || (first == '‘' && last == '’')
                        || (first == '「' && last == '」')
                        || (first == '『' && last == '』')
                        || (first == '《' && last == '》')
                        || (first == '`' && last == '`')
                        || (first == '[' && last == ']')
                        || (first == '(' && last == ')')) {
                    text = text.substring(1, text.length() - 1).trim();
                    continue;
                }
                if (first == '"' || first == '\'' || first == '“' || first == '”'
                        || first == '`' || first == '『' || first == '「') {
                    text = text.substring(1).trim();
                    continue;
                }
            }
            break;
        }
        return text;
    }

    private String stripTrailingPunctuation(String value) {
        String text = value;
        while (!text.isEmpty()) {
            char last = text.charAt(text.length() - 1);
            if ("。.!?！？…~～、,，:：;；".indexOf(last) >= 0) {
                text = text.substring(0, text.length() - 1).trim();
            } else {
                break;
            }
        }
        return text;
    }

    private String stripPrefixes(String value, String... extra) {
        String text = value;
        Set<String> prefixes = PREFIXES;
        boolean changed;
        do {
            changed = false;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                    text = text.substring(prefix.length()).trim();
                    changed = true;
                    break;
                }
            }
            if (!changed && extra != null) {
                for (String prefix : extra) {
                    if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                        text = text.substring(prefix.length()).trim();
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed && !text.isEmpty());
        return text;
    }

    private String capLength(String value) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= MAX_TITLE) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_TITLE));
    }
}
