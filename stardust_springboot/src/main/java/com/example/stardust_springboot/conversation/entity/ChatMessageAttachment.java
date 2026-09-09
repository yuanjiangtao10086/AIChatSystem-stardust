package com.example.stardust_springboot.conversation.entity;

import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "chat_message_attachment")
@EntityListeners(AuditingEntityListener.class)
public class ChatMessageAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private ChatMessage message;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_file_id", nullable = false, updatable = false)
    private UserFile userFile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, updatable = false, length = 16)
    private AttachmentType attachmentType;
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessageAttachment() {
    }

    public ChatMessageAttachment(ChatMessage message, UserFile file, AppUser user, int sortOrder) {
        this.message = message;
        this.userFile = file;
        this.user = user;
        this.sortOrder = sortOrder;
        this.attachmentType = file.getDetectedMime().startsWith("image/")
                ? AttachmentType.IMAGE : AttachmentType.FILE;
    }

    public ChatMessage getMessage() { return message; }
    public UserFile getUserFile() { return userFile; }
    public AttachmentType getAttachmentType() { return attachmentType; }
    public int getSortOrder() { return sortOrder; }
}
