package com.example.stardust_springboot.conversation.repository;

import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
            select conversation from Conversation conversation
            where conversation.deletedAt is null
              and (:userId is null or conversation.user.publicId = :userId)
              and (:from is null or conversation.createdAt >= :from)
              and (:to is null or conversation.createdAt < :to)
              and (:search is null or lower(conversation.title) like lower(concat('%', :search, '%'))
                or exists (select message.id from ChatMessage message
                    where message.conversation = conversation and message.deletedAt is null
                      and lower(message.contentText) like lower(concat('%', :search, '%'))))
            """)
    Page<Conversation> findAdmin(@Param("userId") String userId,
                                 @Param("search") String search,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);
}
