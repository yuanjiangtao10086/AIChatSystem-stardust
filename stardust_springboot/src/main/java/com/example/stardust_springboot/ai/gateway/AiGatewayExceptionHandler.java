package com.example.stardust_springboot.ai.gateway;

import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.exception.GatewayErrorCodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Reports AI gateway failures with the mapped platform error code. Streaming usually converts
 * these into SSE error events, but a failure raised before the stream starts still needs a
 * meaningful REST response.
 */
@RestControllerAdvice
public class AiGatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayExceptionHandler.class);

    @ExceptionHandler(AiGatewayException.class)
    public ResponseEntity<ApiResult<Void>> handleAiGatewayException(AiGatewayException exception) {
        ErrorCode mapped = GatewayErrorCodeMapper.from(exception.code());
        log.warn("AI gateway request rejected: code={} retryable={} mapped={}",
                exception.code(), exception.retryable(), mapped.name());
        return ResponseEntity.status(mapped.httpStatus())
                .body(ApiResult.error(mapped, exception.getMessage(), List.of()));
    }
}
