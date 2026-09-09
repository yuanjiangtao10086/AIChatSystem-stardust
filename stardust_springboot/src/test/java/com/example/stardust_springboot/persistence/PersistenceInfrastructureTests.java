package com.example.stardust_springboot.persistence;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.ai.entity.ModelType;
import com.example.stardust_springboot.ai.entity.ProviderType;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.Role;
import com.example.stardust_springboot.user.entity.UserRole;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.RoleRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PersistenceInfrastructureTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private AiModelRepository modelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    void flywayCreatesCoreTablesAndOwnershipIndexes() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('app_user', 'app_role', 'app_user_role', 'conversation',
                                     'chat_message', 'ai_provider', 'ai_model')
                """, Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE LOWER(index_name) IN ('idx_app_user_status_created',
                                            'idx_conversation_user_status_last',
                                            'idx_message_conversation_sequence',
                                            'idx_message_user_status_created',
                                            'idx_ai_provider_status_type',
                                            'idx_ai_model_status_type_sort')
                """, Integer.class);
        Integer authSchemaCount = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = 'public' AND table_name = 'refresh_token')
                  + (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'auth_version')
                  + (SELECT COUNT(*) FROM information_schema.indexes
                     WHERE LOWER(index_name) = 'idx_refresh_token_user_status_expires')
                """, Integer.class);
        Integer builtInRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM app_role
                WHERE code IN ('USER', 'ADMIN', 'SUPER_ADMIN')
                  AND built_in = TRUE
                  AND status = 'ENABLED'
                """, Integer.class);
        Integer stageThreeMessageColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'chat_message'
                  AND column_name IN ('total_tokens', 'error_message')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(7);
        assertThat(indexCount).isEqualTo(6);
        assertThat(authSchemaCount).isEqualTo(3);
        assertThat(builtInRoleCount).isEqualTo(3);
        assertThat(stageThreeMessageColumnCount).isEqualTo(2);
    }

    @Test
    void persistsAuditedCoreGraphAndScopesOwnershipQueries() {
        AppUser user = userRepository.saveAndFlush(
                new AppUser("owner@example.com", "test-only-password-hash", "Owner"));
        Role role = roleRepository.findByCode("USER").orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(user, role, null));

        AiProvider provider = providerRepository.saveAndFlush(new AiProvider(
                "primary", "Primary", ProviderType.OPENAI_COMPATIBLE,
                "https://provider.example/v1", "secret://ai/primary"));
        AiModel model = modelRepository.saveAndFlush(new AiModel(
                provider, "default-chat", "model-name", "Default Chat", ModelType.CHAT));
        Conversation conversation = conversationRepository.saveAndFlush(new Conversation(user, "New chat"));
        ChatMessage message = messageRepository.saveAndFlush(new ChatMessage(
                conversation, user, MessageRole.USER, 1, 0, "hello"));

        assertThat(user.getPublicId()).hasSize(26);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(provider.getPublicId()).hasSize(26);
        assertThat(model.getPublicId()).hasSize(26);
        assertThat(conversationRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(
                conversation.getPublicId(), user.getId())).contains(conversation);
        assertThat(messageRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(
                message.getPublicId(), user.getId())).contains(message);
        assertThat(userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())).isTrue();

        user.softDelete();
        userRepository.saveAndFlush(user);
        assertThat(userRepository.findByPublicIdAndDeletedAtIsNull(user.getPublicId())).isEmpty();
    }

    @Test
    void databaseRejectsMessageOwnedByDifferentUserThanConversation() {
        AppUser conversationOwner = userRepository.saveAndFlush(
                new AppUser("conversation-owner@example.com", "test-only-password-hash", "Owner"));
        AppUser otherUser = userRepository.saveAndFlush(
                new AppUser("other-user@example.com", "test-only-password-hash", "Other"));
        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(conversationOwner, "Ownership constraint"));

        ChatMessage invalidMessage = new ChatMessage(
                conversation, otherUser, MessageRole.USER, 1, 0, "must be rejected");

        assertThatThrownBy(() -> messageRepository.saveAndFlush(invalidMessage))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
