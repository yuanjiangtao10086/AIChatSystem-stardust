package com.example.stardust_springboot.conversation.entity;

public enum FinishReason {
    STOP,
    LENGTH,
    USER_CANCELLED,
    CONTENT_FILTER,
    TOOL_CALL,
    ERROR
}
