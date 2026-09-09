package com.example.stardust_springboot.memory.service;

import com.example.stardust_springboot.config.MemoryProperties;
import com.example.stardust_springboot.conversation.memory.TokenCounter;
import com.example.stardust_springboot.memory.entity.UserMemory;
import com.example.stardust_springboot.memory.repository.UserMemoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;

@Service
public class MemoryRetriever {
 private final UserMemoryRepository repository; private final MemoryProperties properties; private final TokenCounter tokens;
 public MemoryRetriever(UserMemoryRepository r,MemoryProperties p,TokenCounter t){repository=r;properties=p;tokens=t;}
 @Transactional(readOnly=true)
 public List<RetrievedMemory> retrieve(Long userId,String query){
  String normalized=normalize(query); if(normalized.isBlank()) return List.of();
  Set<String> terms=terms(normalized);
  List<Scored> ranked=repository.findRetrievalCandidates(userId,PageRequest.of(0,properties.retrievalCandidateLimit())).stream()
   .map(m->new Scored(m,score(m,normalized,terms))).filter(s->s.score>0)
   .sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s->s.memory.getUpdatedAt(),Comparator.reverseOrder())).toList();
  List<RetrievedMemory> result=new ArrayList<>(); int used=0;
  for(Scored item:ranked){if(result.size()>=properties.retrievalLimit()) break;
   int cost=tokens.count(item.memory.getSummary())+6; if(used+cost>properties.tokenBudget()) continue;
   result.add(new RetrievedMemory(item.memory.getPublicId(),item.memory.getSummary(),item.memory.getMemoryType(),item.memory.getImportance(),item.score)); used+=cost;}
  return List.copyOf(result);
 }
 private double score(UserMemory m,String query,Set<String> terms){String hay=normalize(m.getSummary()+" "+m.getContent()); double relevance=hay.contains(query)?4:0; for(String term:terms) if(hay.contains(term)) relevance+=1; return relevance==0?0:relevance+m.getImportance()/100.0;}
 private Set<String> terms(String text){Set<String> out=new LinkedHashSet<>(); for(String s:text.split("[^\\p{L}\\p{N}]+")) if(s.length()>=2) out.add(s); if(text.codePoints().anyMatch(c->c>127)) for(int i=0;i<text.length()-1;i++) out.add(text.substring(i,i+2)); return out;}
 private String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();}
 private record Scored(UserMemory memory,double score){}
 public record RetrievedMemory(String id,String summary,com.example.stardust_springboot.memory.entity.MemoryType type,int importance,double relevance){}
}
