package com.example.stardust_springboot.memory.dto;
import com.example.stardust_springboot.memory.entity.MemoryType;
import jakarta.validation.constraints.*;
public record UpdateMemoryRequest(@Size(min=1,max=2000) String content,@Size(min=1,max=300) String summary,
 MemoryType memoryType,@Min(1) @Max(100) Integer importance){}
