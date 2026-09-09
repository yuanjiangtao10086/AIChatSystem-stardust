package com.example.stardust_springboot.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "success"),
    VALIDATION_FAILED(40001, HttpStatus.BAD_REQUEST, "request validation failed"),
    MALFORMED_REQUEST(40002, HttpStatus.BAD_REQUEST, "request body is malformed"),
    BUSINESS_RULE_VIOLATION(40003, HttpStatus.BAD_REQUEST, "business rule violation"),
    CURRENT_PASSWORD_INVALID(40004, HttpStatus.BAD_REQUEST, "current password is invalid"),
    IDEMPOTENCY_CONFLICT(40009, HttpStatus.BAD_REQUEST, "idempotency key conflicts with request"),
    CONTEXT_WINDOW_EXCEEDED(40010, HttpStatus.BAD_REQUEST, "message exceeds model context window"),
    TOKEN_INVALID(40101, HttpStatus.UNAUTHORIZED, "access token is invalid"),
    TOKEN_EXPIRED(40102, HttpStatus.UNAUTHORIZED, "access token is expired"),
    REFRESH_REUSED(40103, HttpStatus.UNAUTHORIZED, "refresh token reuse detected"),
    INVALID_CREDENTIALS(40104, HttpStatus.UNAUTHORIZED, "email or password is invalid"),
    REFRESH_TOKEN_INVALID(40105, HttpStatus.UNAUTHORIZED, "refresh token is invalid or expired"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "access is forbidden"),
    ACCOUNT_BANNED(40302, HttpStatus.FORBIDDEN, "account is banned"),
    RESOURCE_NOT_OWNED(40303, HttpStatus.FORBIDDEN, "resource is not owned by current user"),
    ACCOUNT_DISABLED(40304, HttpStatus.FORBIDDEN, "account is disabled"),
    CSRF_GUARD_REQUIRED(40305, HttpStatus.FORBIDDEN, "CSRF guard header is required"),
    RESOURCE_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "resource was not found"),
    RESOURCE_STATE_CONFLICT(40901, HttpStatus.CONFLICT, "resource state conflicts with operation"),
    EMAIL_ALREADY_EXISTS(40903, HttpStatus.CONFLICT, "email is already registered"),
    FILE_TOO_LARGE(41301, HttpStatus.CONTENT_TOO_LARGE, "file is too large"),
    FILE_TYPE_NOT_ALLOWED(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "file type is not allowed"),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "request rate limit exceeded"),
    AI_QUOTA_EXCEEDED(42902, HttpStatus.TOO_MANY_REQUESTS, "AI quota exceeded"),
    STORAGE_QUOTA_EXCEEDED(42903, HttpStatus.TOO_MANY_REQUESTS, "storage quota exceeded"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "internal server error"),
    PERSISTENCE_ERROR(50002, HttpStatus.INTERNAL_SERVER_ERROR, "persistence operation failed"),
    STORAGE_ERROR(50003, HttpStatus.INTERNAL_SERVER_ERROR, "storage operation failed"),
    AI_SERVICE_UNAVAILABLE(50201, HttpStatus.BAD_GATEWAY, "AI service is unavailable"),
    PROVIDER_ERROR(50202, HttpStatus.BAD_GATEWAY, "AI provider returned an error"),
    AI_TIMEOUT(50401, HttpStatus.GATEWAY_TIMEOUT, "AI request timed out");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
