package com.example.stardust_springboot.auth.entity;

public enum RefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    EXPIRED,
    REUSED
}
