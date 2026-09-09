package com.example.stardust_springboot.conversation;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.ai.entity.ModelType;
import com.example.stardust_springboot.ai.entity.ProviderType;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.example.stardust_springboot.ai.stream.PreparedAiStream;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.memory.ConversationContextBuilder;
import com.example.stardust_springboot.conversation.memory.ConversationSummary;
import com.example.stardust_springboot.conversation.memory.ConversationSummaryRepository;
import com.example.stardust_springboot.conversation.memory.ConversationSummaryService;
import com.example.stardust_springboot.conversation.memory.TokenCounter;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.memory.dto.CreateMemoryRequest;
import com.example.stardust_springboot.memory.entity.MemoryType;
import com.example.stardust_springboot.memory.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.ai.context.default-context-window=256",
        "app.ai.context.default-output-reserve=64",
        "app.ai.context.safety-reserve=16",
        "app.ai.context.max-recent-messages=12",
        "app.ai.context.summary-trigger-messages=8",
        "app.ai.context.summary-trigger-tokens=80",
        "app.ai.context.recent-message-budget=100",
        "app.ai.context.keep-recent-messages=3",
        "app.ai.context.max-summary-tokens=48",
        "app.ai.memory.retrieval-limit=2",
        "app.ai.memory.retrieval-candidate-limit=10",
        "app.ai.memory.token-budget=32"
})
@Transactional
class ConversationShortMemoryIntegrationTests {
    @Autowired private AppUserRepository users;
    @Autowired private ConversationRepository conversations;
    @Autowired private ChatMessageRepository messages;
    @Autowired private AiProviderRepository providers;
    @Autowired private AiModelRepository models;
    @Autowired private ConversationContextBuilder contextBuilder;
    @Autowired private ConversationSummaryService summaryService;
    @Autowired private ConversationSummaryRepository summaries;
    @Autowired private TokenCounter tokens;
    @Autowired private MemoryService memoryService;

    private AppUser user;
    private Conversation conversation;
    private AiModel model;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        user = users.saveAndFlush(new AppUser(suffix + "@example.test", "hash", "Memory Tester"));
        conversation = conversations.saveAndFlush(new Conversation(user, "Short memory test"));
        AiProvider provider = new AiProvider("memory-" + suffix.substring(0, 10), "Memory Provider",
                ProviderType.OPENAI_COMPATIBLE, "https://example.test/v1", null);
        provider.enable();
        providers.saveAndFlush(provider);
        model = new AiModel(provider, "memory-model-" + suffix.substring(0, 10), "memory-model",
                "Memory Model", ModelType.CHAT);
        model.enable();
        models.saveAndFlush(model);
    }

    @Test
    void shortConversationUsesOriginalMessagesAndCurrentUserExactlyOnce() {
        List<ChatMessage> chain = appendCompletedChain(3, null, "short");
        ChatMessage current = chain.getLast();

        List<AiGatewayRequest.AiGatewayMessage> context = contextBuilder.build(current, model);

        assertThat(context).extracting(AiGatewayRequest.AiGatewayMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(context.stream().filter(item -> item.content().equals(current.getContentText())).count())
                .isEqualTo(1);
        assertThat(context).noneMatch(item -> item.content().contains("<conversation_summary>"));
    }

    @Test
    void longConversationCreatesBranchAwareSummaryWithinTokenBudget() {
        List<ChatMessage> chain = appendCompletedChain(8, null, "long");
        ChatMessage assistant = chain.getLast();
        summaryService.refresh(prepared(assistant));

        List<ConversationSummary> stored = summaries.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getSourceMessageCount()).isEqualTo(5);

        ChatMessage current = appendCompleted(MessageRole.USER, assistant, "current-user-message");
        List<AiGatewayRequest.AiGatewayMessage> context = contextBuilder.build(current, model);

        assertThat(context).anyMatch(item -> item.content().contains("<conversation_summary>"));
        assertThat(context.stream().filter(item -> item.content().equals("current-user-message")).count())
                .isEqualTo(1);
        assertThat(context.stream().mapToInt(item -> tokens.countMessage(item.role(), item.content())).sum())
                .isLessThanOrEqualTo(176);

        ChatMessage branchCurrent = appendCompleted(MessageRole.USER, chain.getFirst(), "edited-branch-current");
        List<AiGatewayRequest.AiGatewayMessage> branchContext = contextBuilder.build(branchCurrent, model);
        assertThat(branchContext).noneMatch(item -> item.content().contains("<conversation_summary>"));
        assertThat(branchContext.stream().filter(item -> item.content().equals("edited-branch-current")).count())
                .isEqualTo(1);
    }

    @Test
    void repeatedRefreshRollsSummaryForwardWithoutReadingUnboundedHistory() {
        List<ChatMessage> chain = appendCompletedChain(8, null, "first");
        summaryService.refresh(prepared(chain.getLast()));
        List<ChatMessage> next = appendCompletedChain(8, chain.getLast(), "second");

        summaryService.refresh(prepared(next.getLast()));

        assertThat(summaries.findAll()).hasSize(2);
        ConversationSummary latest = summaries.findAll().stream()
                .max((left, right) -> Integer.compare(left.getSourceMessageCount(), right.getSourceMessageCount()))
                .orElseThrow();
        assertThat(latest.getSourceMessageCount()).isGreaterThan(5);
        assertThat(latest.getEstimatedTokens()).isLessThanOrEqualTo(48);
    }

    @Test
    void tokenThresholdTriggersSummaryBeforeMessageCountThreshold() {
        List<ChatMessage> chain = appendCompletedChain(3, null, "token");
        ChatMessage longAssistant = appendCompleted(MessageRole.ASSISTANT, chain.getLast(), "中".repeat(90));

        summaryService.refresh(prepared(longAssistant));

        assertThat(summaries.findAll()).hasSize(1);
        assertThat(summaries.findAll().getFirst().getSourceMessageCount()).isPositive();
    }

    @Test
    void currentMessageBeyondInputBudgetFailsBeforeCallingProvider() {
        ChatMessage oversized = appendCompleted(MessageRole.USER, null, "界".repeat(200));

        assertThatThrownBy(() -> contextBuilder.build(oversized, model))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONTEXT_WINDOW_EXCEEDED));
    }

    @Test
    void relevantLongTermMemoryIsInjectedAsSeparateBoundedContext() {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getPublicId(),
                user.getEmailNormalized(),
                user.getDisplayName(),
                0,
                Set.of("USER")
        );
        memoryService.create(principal, new CreateMemoryRequest(
                "The Stardust project uses Vue for its user interface.",
                "Stardust uses Vue",
                MemoryType.PROJECT,
                90,
                true
        ));
        ChatMessage current = appendCompleted(
                MessageRole.USER,
                null,
                "How should the Stardust Vue interface evolve?"
        );

        List<AiGatewayRequest.AiGatewayMessage> context = contextBuilder.build(current, model);

        assertThat(context).anyMatch(item -> item.role().equals("system")
                && item.content().contains("<user_memories>")
                && item.content().contains("Stardust uses Vue"));
        assertThat(context.stream().filter(item -> item.content().equals(current.getContentText())).count())
                .isEqualTo(1);
    }

    private List<ChatMessage> appendCompletedChain(int count, ChatMessage parent, String prefix) {
        List<ChatMessage> result = new ArrayList<>();
        ChatMessage currentParent = parent;
        for (int index = 0; index < count; index++) {
            MessageRole role = index % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT;
            ChatMessage message = appendCompleted(role, currentParent, prefix + "-message-" + index);
            result.add(message);
            currentParent = message;
        }
        return result;
    }

    private ChatMessage appendCompleted(MessageRole role, ChatMessage parent, String content) {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        ChatMessage message = new ChatMessage(conversation, user, role,
                conversation.appendMessage(now), 0, content);
        message.setParentMessage(parent);
        message.complete(now, null, null);
        conversations.save(conversation);
        return messages.saveAndFlush(message);
    }

    private PreparedAiStream prepared(ChatMessage assistant) {
        return new PreparedAiStream("request-test", user.getId(), conversation.getId(), assistant.getId(),
                0L, conversation.getPublicId(), "user-message", assistant.getPublicId(), model.getPublicId(),
                model.getProvider().getCode(), model.getExternalModelId(), "SEND", List.of());
    }
}
