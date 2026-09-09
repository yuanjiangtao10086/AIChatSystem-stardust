package com.example.stardust_springboot.user.dto;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.user.entity.AppUser;

import java.util.Set;

public record UserView(
        String id,
        String email,
        String displayName,
        String status,
        Set<String> roles
) {
    public static UserView from(AppUser user, Set<String> roles) {
        return new UserView(user.getPublicId(), user.getEmailNormalized(), user.getDisplayName(),
                user.getStatus().name(), roles);
    }

    public static UserView from(AuthenticatedUser user) {
        return new UserView(user.publicId(), user.email(), user.displayName(), "NORMAL", user.roles());
    }
}
