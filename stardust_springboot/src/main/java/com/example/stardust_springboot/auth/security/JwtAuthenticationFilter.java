package com.example.stardust_springboot.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserSecurityService userSecurityService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   UserSecurityService userSecurityService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.userSecurityService = userSecurityService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            authenticationEntryPoint.commence(request, response,
                    new ApiAuthenticationException(com.example.stardust_springboot.common.exception.ErrorCode.TOKEN_INVALID));
            return;
        }

        try {
            AccessTokenClaims claims = jwtTokenService.parseAndValidate(
                    authorization.substring(BEARER_PREFIX.length()));
            AuthenticatedUser user = userSecurityService.loadActiveUser(claims.subject());
            if (user.authVersion() != claims.authVersion()) {
                throw new ApiAuthenticationException(
                        com.example.stardust_springboot.common.exception.ErrorCode.TOKEN_INVALID);
            }
            var authorities = user.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
