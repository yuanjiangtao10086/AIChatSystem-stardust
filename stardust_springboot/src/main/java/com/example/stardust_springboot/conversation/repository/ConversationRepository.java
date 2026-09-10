package com.example.stardust_springboot.conversation.repository;

import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByPublicIdAndUserIdAndDeletedAtIsNull(String publicId, Long userId);

    @Query("""
            select conversation from Conversation conversation
            where conversation.user.id = :userId
              and conversation.deletedAt is null
              and (:status is null or conversation.status = :status)
              and (:search is null or lower(conversation.title) like lower(concat('%', :search, '%')))
            """)
    Page<Conversation> findAllOwned(@Param("userId") Long userId,
                                    @Param("status") ConversationStatus status,
                                    @Param("search") String search,
                                    Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select conversation from Conversation conversation
            where conversation.publicId = :publicId
              and conversation.user.id = :userId
              and conversation.deletedAt is null
            """)
    Optional<Conversation> findOwnedForUpdate(@Param("publicId") String publicId,
                                              @Param("userId") Long userId);

    Optional<Conversation> findByPublicIdAndDeletedAtIsNull(String publicId);

    @Modifying
    @Query("""
            update Conversation conversation
            set conversation.status = com.example.stardust_springboot.conversation.entity.ConversationStatus.DELETED,
                conversation.deletedAt = :now,
                conversation.updatedAt = :now
            where conversation.user.id = :userId
              and conversation.deletedAt is null
              and conversation.status <> com.example.stardust_springboot.conversation.entity.ConversationStatus.DELETED
              and conversation.messageCount = 0
              and (:keepId is null or conversation.publicId <> :keepId)
            """)
    int softDeleteEmptyOwned(@Param("userId") Long userId,
                             @Param("keepId") String keepId,
                             @Param("now") Instant now);

    @Query("""
            select conversation from Conversation conversation
            where conversation.deletedAt is null
              and (:userId is null or conversation.user.publicId = :userId)
              and (:from is null or conversation.createdAt >= :from)
              and (:to is null or conversation.createdAt < :to)
              and (:search is null or lower(conversation.title) like lower(concat('%', :search, '%'))
                or lower(conversation.user.displayName) like lower(concat('%', :search, '%'))
                or lower(conversation.user.emailNormalized) like lower(concat('%', :search, '%'))
                or exists (select message.id from ChatMessage message
                    where message.conversation = conversation and message.deletedAt is null
                      and lower(message.contentText) like lower(concat('%', :search, '%'))))
              and (:hideSuperAdminOwned = false or not exists (
                    select ur.id from UserRole ur
                    where ur.user = conversation.user
                      and ur.role.code = 'SUPER_ADMIN'
                      and ur.role.status = com.example.stardust_springboot.user.entity.RoleStatus.ENABLED))
            """)
    Page<Conversation> findAdmin(@Param("userId") String userId,
                                 @Param("search") String search,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("hideSuperAdminOwned") boolean hideSuperAdminOwned,
                                 Pageable pageable);
}
