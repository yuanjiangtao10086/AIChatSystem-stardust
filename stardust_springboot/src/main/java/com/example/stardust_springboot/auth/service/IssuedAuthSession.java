package com.example.stardust_springboot.auth.service;

import com.example.stardust_springboot.auth.dto.AuthResponse;

public record IssuedAuthSession(AuthResponse response, String refreshToken) {
}
