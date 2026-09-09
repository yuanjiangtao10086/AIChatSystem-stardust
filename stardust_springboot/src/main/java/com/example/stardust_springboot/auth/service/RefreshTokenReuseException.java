package com.example.stardust_springboot.auth.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;

public class RefreshTokenReuseException extends BusinessException {

    public RefreshTokenReuseException() {
        super(ErrorCode.REFRESH_REUSED);
    }
}
