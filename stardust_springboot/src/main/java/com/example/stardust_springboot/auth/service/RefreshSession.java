package com.example.stardust_springboot.auth.service;

import com.example.stardust_springboot.user.entity.AppUser;

public record RefreshSession(AppUser user, String rawToken) {
}
