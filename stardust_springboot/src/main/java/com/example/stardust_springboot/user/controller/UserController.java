package com.example.stardust_springboot.user.controller;

import com.example.stardust_springboot.auth.dto.AuthResponse;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.auth.service.AuthService;
import com.example.stardust_springboot.auth.service.IssuedAuthSession;
import com.example.stardust_springboot.auth.service.RefreshCookieService;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.user.dto.ChangePasswordRequest;
import com.example.stardust_springboot.user.dto.RolesView;
import com.example.stardust_springboot.user.dto.UpdateProfileRequest;
import com.example.stardust_springboot.user.dto.UserView;
import com.example.stardust_springboot.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;

    public UserController(UserService userService, AuthService authService,
                          RefreshCookieService refreshCookieService) {
        this.userService = userService;
        this.authService = authService;
        this.refreshCookieService = refreshCookieService;
    }

    @GetMapping
    public ApiResult<UserView> current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResult.success(userService.current(principal));
    }

    @PatchMapping
    public ApiResult<UserView> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResult.success(userService.updateProfile(principal, request));
    }

    @PutMapping("/password")
    public ApiResult<AuthResponse> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response
    ) {
        IssuedAuthSession session = authService.changePassword(principal, request);
        refreshCookieService.write(response, session.refreshToken());
        return ApiResult.success(session.response());
    }

    @GetMapping("/permissions")
    public ApiResult<RolesView> roles(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResult.success(new RolesView(userService.roles(principal)));
    }
}
