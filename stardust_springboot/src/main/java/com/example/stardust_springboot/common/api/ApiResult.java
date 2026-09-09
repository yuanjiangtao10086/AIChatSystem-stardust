package com.example.stardust_springboot.common.api;

import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.logging.RequestContext;

import java.time.Instant;
import java.util.List;

public record ApiResult<T>(
        int code,
        String message,
        T data,
        String requestId,
        Instant timestamp,
        List<ValidationError> errors
) {
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data,
                RequestContext.requestId(), Instant.now(), null);
    }

    public static ApiResult<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.message(), List.of());
    }

    public static ApiResult<Void> error(
            ErrorCode errorCode,
            String message,
            List<ValidationError> validationErrors
    ) {
        return new ApiResult<>(errorCode.code(), message, null, RequestContext.requestId(),
                Instant.now(), List.copyOf(validationErrors));
    }
}
