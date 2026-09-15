package com.example.stardust_springboot.memory.service;

import com.example.stardust_springboot.ai.stream.PreparedAiStream;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.BatchDeleteResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.*;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.memory.dto.*;
import com.example.stardust_springboot.memory.entity.*;
import com.example.stardust_springboot.memory.repository.UserMemoryRepository;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets; import java.security.*; import java.text.Normalizer; import java.util.ArrayList; import java.util.HexFormat; import java.util.List;

@Service
public class MemoryService {
 private final UserMemoryRepository repo; private final AppUserRepository users; private final ChatMessageRepository messages; private final MemoryExtractor extractor;
 public MemoryService(UserMemoryRepository r,AppUserRepository u,ChatMessageRepository m,MemoryExtractor e){repo=r;users=u;messages=m;extractor=e;}
 @Transactional(readOnly=true) public PageResult<MemoryView> list(AuthenticatedUser p,int page,int size,String search,Boolean enabled,MemoryType type){String q=cleanNullable(search); return PageResult.from(repo.findOwned(p.id(),q,enabled,type,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"updatedAt").and(Sort.by(Sort.Direction.DESC,"id")))).map(MemoryView::from));}
 @Transactional(readOnly=true) public MemoryView get(AuthenticatedUser p,String id){return MemoryView.from(require(p.id(),id));}
 @Transactional public MemoryView create(AuthenticatedUser p,CreateMemoryRequest r){String content=cleanRequired(r.content()), summary=cleanRequired(r.summary()); UserMemory m=new UserMemory(users.findById(p.id()).orElseThrow(),content,summary,r.memoryType(),r.importance(),null,null,MemoryOrigin.MANUAL,hash(content)); m.setEnabled(r.enabled()==null||r.enabled()); return MemoryView.from(repo.save(m));}
 @Transactional public MemoryView update(AuthenticatedUser p,String id,UpdateMemoryRequest r){UserMemory m=require(p.id(),id); String content=r.content()==null?m.getContent():cleanRequired(r.content()); m.update(content,r.summary()==null?m.getSummary():cleanRequired(r.summary()),r.memoryType()==null?m.getMemoryType():r.memoryType(),r.importance()==null?m.getImportance():r.importance(),hash(content)); return MemoryView.from(m);}
 @Transactional public MemoryView enabled(AuthenticatedUser p,String id,boolean enabled){UserMemory m=require(p.id(),id);m.setEnabled(enabled);return MemoryView.from(m);}
 @Transactional public void delete(AuthenticatedUser p,String id){require(p.id(),id).softDelete();}
 /** Deletes many memories, one transaction per id (each delete() opens its own tx). Failures are reported per id and never abort the batch. */
 public BatchDeleteResult batchDelete(AuthenticatedUser p,List<String> ids){
  long deleted=0; List<BatchDeleteResult.BatchDeleteFailure> failures=new ArrayList<>();
  for(String id:ids){ try{ delete(p,id); deleted++; }
   catch(BusinessException e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,String.valueOf(e.getErrorCode().code()),e.getMessage())); }
   catch(Exception e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,"UNEXPECTED",e.getMessage())); } }
  return BatchDeleteResult.of(deleted,failures);
 }
 @Transactional public void extractFromCompletedStream(PreparedAiStream stream){var message=messages.findByPublicIdAndUserIdAndDeletedAtIsNull(stream.userMessageId(),stream.userId()).orElseThrow(); if(message.getRole()!=com.example.stardust_springboot.conversation.entity.MessageRole.USER||message.getStatus()!=com.example.stardust_springboot.conversation.entity.MessageStatus.COMPLETED)return; extractor.extract(message.getContentText()).ifPresent(x->{String h=hash(x.content()); if(repo.findFirstByUserIdAndContentHashAndDeletedAtIsNull(stream.userId(),h).isEmpty()) repo.save(new UserMemory(message.getUser(),x.content(),x.summary(),x.type(),x.importance(),message.getConversation(),message,MemoryOrigin.AUTO,h));});}
 private UserMemory require(Long uid,String id){return repo.findByPublicIdAndUserIdAndDeletedAtIsNull(id,uid).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
 private String cleanRequired(String s){String value=normalize(s);if(value.isEmpty())throw new BusinessException(ErrorCode.VALIDATION_FAILED);return value;}
 private String cleanNullable(String s){if(s==null)return null;String value=normalize(s);return value.isEmpty()?null:value;}
 private String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).replaceAll("\\s+"," ").trim();}
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(s).toLowerCase().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
