package com.example.stardust_springboot.common.exception;

/**
 * Translates the structured error codes returned by the Python AI service into the platform
 * {@link ErrorCode} contract.
 *
 * <p>Without this translation every gateway failure (429/502/504/413) reaches the generic handler
 * and is reported to callers as {@code 500 internal server error}, which hides both the retry
 * semantics and the real cause.
 */
public final class GatewayErrorCodeMapper {

    private GatewayErrorCodeMapper() {
    }

    public static ErrorCode from(String code) {
        if (code == null || code.isBlank()) {
            return ErrorCode.AI_SERVICE_UNAVAILABLE;
        }
        return switch (code) {
            case "PROVIDER_TIMEOUT" -> ErrorCode.AI_TIMEOUT;
            case "PROVIDER_RATE_LIMITED" -> ErrorCode.RATE_LIMITED;
            case "PROVIDER_UNAVAILABLE", "SERVICE_NOT_CONFIGURED" -> ErrorCode.AI_SERVICE_UNAVAILABLE;
            case "PROVIDER_ERROR",
                 "PROVIDER_PROTOCOL_ERROR",
                 "PROVIDER_REQUEST_REJECTED",
                 "PROVIDER_AUTHENTICATION_FAILED",
                 "PROVIDER_RESOURCE_NOT_FOUND" -> ErrorCode.PROVIDER_ERROR;
            case "DOCUMENT_TOO_LARGE" -> ErrorCode.FILE_TOO_LARGE;
            case "SCHEMA_VALIDATION_ERROR" -> ErrorCode.VALIDATION_FAILED;
            case "INTERNAL_UNAUTHORIZED" -> ErrorCode.FORBIDDEN;
            default -> ErrorCode.AI_SERVICE_UNAVAILABLE;
        };
    }
}
