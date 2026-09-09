package com.example.stardust_springboot.memory.dto;
import com.example.stardust_springboot.memory.entity.MemoryType;
import jakarta.validation.constraints.*;
public record CreateMemoryRequest(@NotBlank @Size(max=2000) String content,@NotBlank @Size(max=300) String summary,
 @NotNull MemoryType memoryType,@NotNull @Min(1) @Max(100) Integer importance,Boolean enabled){}
