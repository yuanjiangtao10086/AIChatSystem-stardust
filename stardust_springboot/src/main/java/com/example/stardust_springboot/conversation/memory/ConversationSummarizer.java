package com.example.stardust_springboot.conversation.memory;

import com.example.stardust_springboot.config.ConversationContextProperties;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A bounded, deterministic first implementation. It deliberately has no provider dependency so
 * summary refresh cannot recursively create an AI stream. A model-backed implementation can
 * replace this component later without changing context construction or persistence.
 */
@Component
public class ConversationSummarizer {
    private static final int MAX_MESSAGE_CHARS = 600;
    private static final String OMITTED_PREFIX = "[Earlier history omitted during bounded bootstrap.]\n";

    private final TokenCounter tokens;
    private final ConversationContextProperties properties;

    public ConversationSummarizer(TokenCounter tokens, ConversationContextProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    public String summarize(String previousSummary, List<ChatMessage> messagesOldestFirst,
                            boolean earlierHistoryOmitted) {
        StringBuilder result = new StringBuilder();
        if (earlierHistoryOmitted && (previousSummary == null || previousSummary.isBlank())) {
            result.append(OMITTED_PREFIX);
        }
        if (previousSummary != null && !previousSummary.isBlank()) {
            result.append(previousSummary.trim()).append('\n');
        }
        for (ChatMessage message : messagesOldestFirst) {
            String content = normalize(message.getContentText());
            if (content.length() > MAX_MESSAGE_CHARS) {
                int end = MAX_MESSAGE_CHARS;
                if (Character.isHighSurrogate(content.charAt(end - 1))) {
                    end--;
                }
                content = content.substring(0, end) + "…";
            }
            result.append(message.getRole().name()).append(": ").append(content).append('\n');
        }
        return compact(result.toString().trim(), properties.maxSummaryTokens());
    }

    private String compact(String value, int tokenBudget) {
        if (tokens.count(value) <= tokenBudget) {
            return value;
        }
        String marker = "[…earlier summary compacted…]\n";
        if (tokens.count(marker) >= tokenBudget) {
            return "…";
        }
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (tokens.count(marker + safeTail(value, mid)) <= tokenBudget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return marker + safeTail(value, low);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String safeTail(String value, int chars) {
        int start = Math.max(0, value.length() - chars);
        if (start < value.length() && Character.isLowSurrogate(value.charAt(start))) {
            start++;
        }
        return value.substring(start);
    }
}
