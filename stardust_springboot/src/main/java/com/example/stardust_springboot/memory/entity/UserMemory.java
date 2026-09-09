package com.example.stardust_springboot.memory.entity;

import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name="user_memory", indexes={
        @Index(name="idx_memory_owner_enabled_importance", columnList="user_id,enabled,deleted_at,importance,updated_at,id"),
        @Index(name="idx_memory_owner_type_updated", columnList="user_id,memory_type,updated_at,id"),
        @Index(name="idx_memory_owner_hash", columnList="user_id,content_hash,deleted_at,id")})
public class UserMemory extends SoftDeleteEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false) private AppUser user;
    @Column(name="content_text", nullable=false, columnDefinition="TEXT") private String content;
    @Column(nullable=false, length=300) private String summary;
    @Enumerated(EnumType.STRING) @Column(name="memory_type", nullable=false, length=24) private MemoryType memoryType;
    @Column(nullable=false) private int importance;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="source_conversation_id") private Conversation sourceConversation;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="source_message_id") private ChatMessage sourceMessage;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private MemoryOrigin origin;
    @Column(name="content_hash", nullable=false, length=64, columnDefinition="CHAR(64)") private String contentHash;
    @Column(nullable=false) private boolean enabled=true;
    protected UserMemory() {}
    public UserMemory(AppUser user, String content, String summary, MemoryType type, int importance,
                      Conversation conversation, ChatMessage message, MemoryOrigin origin, String hash) {
        this.user=user; this.content=content; this.summary=summary; this.memoryType=type;
        this.importance=importance; this.sourceConversation=conversation; this.sourceMessage=message;
        this.origin=origin; this.contentHash=hash;
    }
    public void update(String content, String summary, MemoryType type, int importance, String hash) {
        this.content=content; this.summary=summary; this.memoryType=type; this.importance=importance; this.contentHash=hash;
    }
    public void setEnabled(boolean enabled) { this.enabled=enabled; }
    public void softDelete() { enabled=false; markDeletedAt(java.time.Instant.now()); }
    public String getContent(){return content;} public String getSummary(){return summary;}
    public MemoryType getMemoryType(){return memoryType;} public int getImportance(){return importance;}
    public boolean isEnabled(){return enabled;} public MemoryOrigin getOrigin(){return origin;}
    public Conversation getSourceConversation(){return sourceConversation;} public ChatMessage getSourceMessage(){return sourceMessage;}
}
