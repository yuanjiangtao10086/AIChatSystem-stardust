package com.example.stardust_springboot.ai.gateway;

public class AiGatewayException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public AiGatewayException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
