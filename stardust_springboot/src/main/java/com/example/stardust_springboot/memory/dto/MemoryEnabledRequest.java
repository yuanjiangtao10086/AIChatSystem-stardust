package com.example.stardust_springboot.memory.dto;
import jakarta.validation.constraints.NotNull;
public record MemoryEnabledRequest(@NotNull Boolean enabled){}
