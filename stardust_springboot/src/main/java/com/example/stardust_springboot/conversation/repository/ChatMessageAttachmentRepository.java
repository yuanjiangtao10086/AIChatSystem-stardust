package com.example.stardust_springboot.conversation.repository;

import com.example.stardust_springboot.conversation.entity.ChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, Long> {
    boolean existsByUserFileId(Long userFileId);
    long countByUserFileId(Long userFileId);

    @Query("""
            select attachment from ChatMessageAttachment attachment
            join fetch attachment.userFile file
            where attachment.message.id in :messageIds
            order by attachment.message.id, attachment.sortOrder, attachment.id
            """)
    List<ChatMessageAttachment> findForMessages(@Param("messageIds") Collection<Long> messageIds);

    /**
     * Attachments of a single message in composer order. The message is always one the caller already
     * loaded through an owner-scoped query, so this method never needs a second ownership predicate.
     */
    @Query("""
            select attachment from ChatMessageAttachment attachment
            join fetch attachment.userFile file
            where attachment.message.id = :messageId
            order by attachment.sortOrder, attachment.id
            """)
    List<ChatMessageAttachment> findByMessage(@Param("messageId") Long messageId);
}
