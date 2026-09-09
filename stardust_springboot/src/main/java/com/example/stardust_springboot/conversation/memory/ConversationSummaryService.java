package com.example.stardust_springboot.conversation.memory;

import com.example.stardust_springboot.ai.stream.PreparedAiStream;
import com.example.stardust_springboot.config.ConversationContextProperties;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.entity.MessageStatus;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationSummaryService {
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ConversationSummaryRepository summaries;
    private final ConversationSummarizer summarizer;
    private final TokenCounter tokens;
    private final ConversationContextProperties properties;

    public ConversationSummaryService(ConversationRepository conversations, ChatMessageRepository messages,
                                      ConversationSummaryRepository summaries, ConversationSummarizer summarizer,
                                      TokenCounter tokens, ConversationContextProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.summaries = summaries;
        this.summarizer = summarizer;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional
    public void refresh(PreparedAiStream stream) {
        Conversation conversation = conversations.findOwnedForUpdate(stream.conversationId(), stream.userId())
                .orElseThrow();
        ChatMessage assistant = messages.findById(stream.assistantMessageDatabaseId()).orElseThrow();
        if (!assistant.getConversation().getId().equals(conversation.getId())
                || !assistant.getUser().getId().equals(stream.userId())
                || assistant.getStatus() != MessageStatus.COMPLETED) {
            return;
        }

        List<ChatMessage> newestFirst = new ArrayList<>();
        ConversationSummary previous = null;
        ChatMessage current = assistant;
        int inspected = 0;
        while (current != null && inspected < properties.maxRecentMessages()) {
            previous = summaries.findByCoveredThroughMessageIdAndConversationIdAndUserIdAndStatus(
                    current.getId(), conversation.getId(), stream.userId(), ConversationSummaryStatus.ACTIVE)
                    .orElse(null);
            if (previous != null) {
                break;
            }
            if (current.getStatus() == MessageStatus.COMPLETED && current.getRole() != MessageRole.TOOL) {
                newestFirst.add(current);
            }
            current = current.getParentMessage();
            inspected++;
        }
        int candidateTokens = newestFirst.stream()
                .mapToInt(message -> tokens.countMessage(
                        message.getRole().name().toLowerCase(), message.getContentText()))
                .sum();
        if (newestFirst.size() < properties.summaryTriggerMessages()
                && candidateTokens < properties.summaryTriggerTokens()) {
            return;
        }

        if (newestFirst.size() < 2) {
            return;
        }
        int keep = 0;
        int keptTokens = 0;
        while (keep < newestFirst.size() - 1 && keep < properties.keepRecentMessages()) {
            ChatMessage candidate = newestFirst.get(keep);
            int messageTokens = tokens.countMessage(
                    candidate.getRole().name().toLowerCase(), candidate.getContentText());
            if (keep > 0 && keptTokens + messageTokens > properties.recentMessageBudget()) {
                break;
            }
            keptTokens += messageTokens;
            keep++;
        }
        ChatMessage anchor = newestFirst.get(keep);
        if (summaries.existsByConversationIdAndCoveredThroughMessageId(conversation.getId(), anchor.getId())) {
            return;
        }
        List<ChatMessage> folded = new ArrayList<>(newestFirst.subList(keep, newestFirst.size()));
        Collections.reverse(folded);
        boolean omitted = previous == null && current != null
                && inspected >= properties.maxRecentMessages();
        String text = summarizer.summarize(previous == null ? null : previous.getSummaryText(), folded, omitted);
        int sourceCount = (previous == null ? 0 : previous.getSourceMessageCount()) + folded.size();
        summaries.save(new ConversationSummary(conversation, conversation.getUser(),
                summaries.findMaxVersion(conversation.getId()) + 1, text, anchor,
                sourceCount, tokens.count(text)));
    }
}
