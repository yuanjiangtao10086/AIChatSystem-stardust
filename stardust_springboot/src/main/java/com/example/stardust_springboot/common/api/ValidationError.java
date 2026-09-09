package com.example.stardust_springboot.common.api;

public record ValidationError(String field, String reason) {
}
