package com.example.stardust_springboot.auth.security;

import com.example.stardust_springboot.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;

public class ApiAuthenticationException extends AuthenticationException {

    private final ErrorCode errorCode;

    public ApiAuthenticationException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
