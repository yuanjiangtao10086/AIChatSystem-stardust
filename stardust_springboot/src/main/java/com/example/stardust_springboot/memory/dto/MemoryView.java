package com.example.stardust_springboot.memory.dto;
import com.example.stardust_springboot.memory.entity.*;
import java.time.Instant;
public record MemoryView(String id,String content,String summary,MemoryType memoryType,int importance,
 Boolean enabled,MemoryOrigin origin,String sourceConversationId,String sourceMessageId,Instant createdAt,Instant updatedAt){
 public static MemoryView from(UserMemory m){return new MemoryView(m.getPublicId(),m.getContent(),m.getSummary(),m.getMemoryType(),m.getImportance(),m.isEnabled(),m.getOrigin(),m.getSourceConversation()==null?null:m.getSourceConversation().getPublicId(),m.getSourceMessage()==null?null:m.getSourceMessage().getPublicId(),m.getCreatedAt(),m.getUpdatedAt());}
}
