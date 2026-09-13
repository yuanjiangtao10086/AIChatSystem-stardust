package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.dto.MessageSearchHitView;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Owner-scoped substring search over chat messages.
 *
 * <p>Deliberately kept separate from {@link MessageService} (history paging) and from the admin
 * conversation search: the user facing search is always owner-scoped, returns bounded snippets instead of
 * whole bodies, and must not grow into a second read model for streaming.
 */
@Service
public class MessageSearchService {

    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public MessageSearchService(ConversationRepository conversationRepository,
                                ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<MessageSearchHitView> searchAll(AuthenticatedUser principal, String keyword,
                                                      int page, int size) {
        String needle = normalizeKeyword(keyword);
        String pattern = toLikePattern(needle);
        Page<ChatMessage> hits = messageRepository.searchOwned(principal.id(), pattern, pageable(page, size));
        return PageResult.from(hits.map(message -> MessageSearchHitView.of(message, needle)));
    }

    @Transactional(readOnly = true)
    public PageResult<MessageSearchHitView> searchInConversation(AuthenticatedUser principal,
                                                                 String conversationId, String keyword,
                                                                 int page, int size) {
        Conversation conversation = conversationRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(conversationId, principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String needle = normalizeKeyword(keyword);
        String pattern = toLikePattern(needle);
        Page<ChatMessage> hits = messageRepository.searchOwnedInConversation(
                conversation.getId(), principal.id(), pattern, pageable(page, size));
        return PageResult.from(hits.map(message -> MessageSearchHitView.of(message, needle)));
    }

    private PageRequest pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    /**
     * NFKC-normalizes and trims the keyword so visually identical text (full-width vs half-width) matches.
     * An empty keyword is a client error: searching for "everything" is never what the user asked for and
     * would only produce a full scan.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            normalized = normalized.substring(0, MAX_KEYWORD_LENGTH);
        }
        return normalized;
    }

    /**
     * Escapes LIKE wildcards so a keyword such as {@code 100%} stays a literal search instead of matching
     * every row. {@code !} is declared as the escape character in the repository queries.
     */
    private String toLikePattern(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length() + 2);
        for (int i = 0; i < keyword.length(); i++) {
            char current = keyword.charAt(i);
            if (current == '!' || current == '%' || current == '_') {
                escaped.append('!');
            }
            escaped.append(current);
        }
        return "%" + escaped.toString().toLowerCase(Locale.ROOT) + "%";
    }
}
