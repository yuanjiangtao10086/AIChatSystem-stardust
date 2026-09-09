package com.example.stardust_springboot.memory.service;

import com.example.stardust_springboot.memory.entity.MemoryType;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class MemoryExtractor {
 private static final Pattern SENSITIVE=Pattern.compile("(?i)(password|passcode|api[ _-]?key|access[ _-]?token|private[ _-]?key|secret|验证码|密码|密钥|令牌|身份证|银行卡|信用卡|社保号)");
 private static final List<Rule> RULES=List.of(
  new Rule(MemoryType.EXPLICIT,90,Pattern.compile("(?i)(?:请记住|记住[:：]?|remember(?: that)?)[，,:： ]*(.+)")),
  new Rule(MemoryType.PREFERENCE,70,Pattern.compile("(?i)(?:我(?:一直)?(?:喜欢|偏好|更喜欢)|I (?:prefer|like))(.+)")),
  new Rule(MemoryType.GOAL,80,Pattern.compile("(?i)(?:我的(?:长期)?目标是|我计划长期|my (?:long-term )?goal is)(.+)")),
  new Rule(MemoryType.PROJECT,75,Pattern.compile("(?i)(?:我正在做|我的(?:长期)?项目是|I am working on|my project is)(.+)")));
 public Optional<ExtractedMemory> extract(String input){
  if(input==null) return Optional.empty(); String text=input.replaceAll("\\s+"," ").trim();
  if(text.length()<4||text.length()>2000||SENSITIVE.matcher(text).find()) return Optional.empty();
  for(Rule rule:RULES){Matcher m=rule.pattern.matcher(text); if(m.find()){
   String content=m.group(m.groupCount()).trim(); if(content.length()<2) return Optional.empty();
   String summary=content.length()<=120?content:content.substring(0,120)+"…";
   return Optional.of(new ExtractedMemory(content,summary,rule.type,rule.importance));}}
  return Optional.empty();
 }
 private record Rule(MemoryType type,int importance,Pattern pattern){}
 public record ExtractedMemory(String content,String summary,MemoryType type,int importance){}
}
