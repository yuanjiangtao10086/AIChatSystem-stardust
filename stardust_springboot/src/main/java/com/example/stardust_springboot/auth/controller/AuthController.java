package com.example.stardust_springboot.auth.controller;

import com.example.stardust_springboot.auth.dto.AuthResponse;
import com.example.stardust_springboot.auth.dto.LoginRequest;
import com.example.stardust_springboot.auth.dto.RegisterRequest;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.auth.service.AuthService;
import com.example.stardust_springboot.auth.service.IssuedAuthSession;
import com.example.stardust_springboot.auth.service.RefreshCookieService;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, RefreshCookieService refreshCookieService,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.refreshCookieService = refreshCookieService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResult<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        IssuedAuthSession session = authService.register(request, clientIpResolver.resolve(servletRequest));
        refreshCookieService.write(response, session.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(session.response()));
    }

    @PostMapping("/login")
    public ApiResult<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletRequest servletRequest,
                                         HttpServletResponse response) {
        IssuedAuthSession session = authService.login(request, clientIpResolver.resolve(servletRequest));
        refreshCookieService.write(response, session.refreshToken());
        return ApiResult.success(session.response());
    }

    @PostMapping("/refresh")
    public ApiResult<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        IssuedAuthSession session = authService.refresh(refreshToken, clientIpResolver.resolve(servletRequest));
        refreshCookieService.write(response, session.refreshToken());
        return ApiResult.success(session.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, principal);
        refreshCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser principal,
                                          HttpServletResponse response) {
        authService.logoutAll(principal);
        refreshCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }
}
