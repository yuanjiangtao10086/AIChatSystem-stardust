package com.example.stardust_springboot.auth.security;

import com.example.stardust_springboot.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class RefreshCsrfGuardFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-CSRF-Guard";
    public static final String EXPECTED_VALUE = "1";
    private static final Set<String> GUARDED_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final SecurityErrorWriter errorWriter;

    public RefreshCsrfGuardFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.POST.matches(request.getMethod())
                && GUARDED_PATHS.contains(request.getRequestURI())
                && !EXPECTED_VALUE.equals(request.getHeader(HEADER))) {
            errorWriter.write(response, ErrorCode.CSRF_GUARD_REQUIRED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
