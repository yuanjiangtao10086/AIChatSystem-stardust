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

    /**
     * Whether the file is still referenced by a live message: the message itself is not soft-deleted
     * and its conversation is not soft-deleted. References held only by deleted conversations must
     * not block file deletion — conversation deletion would otherwise lock referenced files forever.
     */
    @Query("""
            select case when count(attachment) > 0 then true else false end
            from ChatMessageAttachment attachment
            where attachment.userFile.id = :userFileId
              and attachment.message.deletedAt is null
              and attachment.message.conversation.deletedAt is null
            """)
    boolean existsActiveByUserFileId(@Param("userFileId") Long userFileId);

    /** Same semantics as {@link #existsActiveByUserFileId}, for reference counts shown in UIs. */
    @Query("""
            select count(attachment) from ChatMessageAttachment attachment
            where attachment.userFile.id = :userFileId
              and attachment.message.deletedAt is null
              and attachment.message.conversation.deletedAt is null
            """)
    long countActiveByUserFileId(@Param("userFileId") Long userFileId);

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
