package com.example.stardust_springboot.knowledge.gateway;

public class RagGatewayException extends RuntimeException {
    private final String code;

    public RagGatewayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RagGatewayException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
