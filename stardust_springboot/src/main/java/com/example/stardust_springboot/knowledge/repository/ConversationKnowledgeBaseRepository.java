package com.example.stardust_springboot.knowledge.repository;

import com.example.stardust_springboot.knowledge.entity.ConversationKnowledgeBase;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationKnowledgeBaseRepository
        extends JpaRepository<ConversationKnowledgeBase, Long> {
    @Query("""
            select binding from ConversationKnowledgeBase binding
            join fetch binding.knowledgeBase kb
            where binding.conversation.id = :conversationId
              and binding.user.id = :userId
              and kb.deletedAt is null
            order by kb.name asc
            """)
    List<ConversationKnowledgeBase> findOwnedBindings(
            @Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from ConversationKnowledgeBase b where b.conversation.id = :conversationId and b.user.id = :userId")
    void deleteOwnedByConversationId(@Param("conversationId") Long conversationId,
                                     @Param("userId") Long userId);

    @Modifying
    @Query("delete from ConversationKnowledgeBase b where b.knowledgeBase.id = :knowledgeBaseId")
    void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    boolean existsByKnowledgeBaseId(Long knowledgeBaseId);
}
