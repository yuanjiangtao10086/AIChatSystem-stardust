package com.example.stardust_springboot.common.exception;

import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.ValidationError;
import com.example.stardust_springboot.common.logging.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business request rejected: code={}", errorCode.name());
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.error(errorCode, exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ValidationError(error.getField(),
                        error.getCode() == null ? "INVALID" : error.getCode().toUpperCase()))
                .toList();
        log.warn("Request validation failed: fieldCount={}", errors.size());
        return ResponseEntity.badRequest()
                .body(ApiResult.error(ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.message(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().toUpperCase()))
                .sorted(Comparator.comparing(ValidationError::field))
                .toList();
        log.warn("Constraint validation failed: violationCount={}", errors.size());
        return ResponseEntity.badRequest()
                .body(ApiResult.error(ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.message(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMalformedRequest() {
        log.warn("Malformed request body rejected");
        return ResponseEntity.badRequest().body(ApiResult.error(ErrorCode.MALFORMED_REQUEST));
    }

    /**
     * A persistence failure is the hardest class of error to diagnose from a client report, so the
     * server log gets the endpoint, the request id and the deepest cause. The client only ever sees
     * the generic {@code 50002} envelope — database details never leave the server.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResult<Void>> handlePersistenceException(DataAccessException exception,
                                                                     HttpServletRequest request) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.error("Persistence request failed: method={} uri={} requestId={} exceptionType={} rootType={} rootMessage={}",
                request.getMethod(), request.getRequestURI(), RequestContext.requestId(),
                exception.getClass().getName(), root.getClass().getName(), root.getMessage(), exception);
        return ResponseEntity.status(ErrorCode.PERSISTENCE_ERROR.httpStatus())
                .body(ApiResult.error(ErrorCode.PERSISTENCE_ERROR));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSize() {
        log.warn("Multipart upload exceeded configured size limit");
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.httpStatus())
                .body(ApiResult.error(ErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request failure: exceptionType={}", exception.getClass().getName(), exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResult.error(ErrorCode.INTERNAL_ERROR));
    }
}
