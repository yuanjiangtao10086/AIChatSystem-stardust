package com.example.stardust_springboot.knowledge.gateway;

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
 * Reports RAG gateway failures with the mapped platform error code instead of letting them fall
 * through to the generic 500 handler.
 */
@RestControllerAdvice
public class RagGatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RagGatewayExceptionHandler.class);

    @ExceptionHandler(RagGatewayException.class)
    public ResponseEntity<ApiResult<Void>> handleRagGatewayException(RagGatewayException exception) {
        ErrorCode mapped = GatewayErrorCodeMapper.from(exception.getCode());
        log.warn("RAG gateway request rejected: code={} mapped={}", exception.getCode(), mapped.name());
        return ResponseEntity.status(mapped.httpStatus())
                .body(ApiResult.error(mapped, exception.getMessage(), List.of()));
    }
}
