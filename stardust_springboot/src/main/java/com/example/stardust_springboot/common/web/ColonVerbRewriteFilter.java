package com.example.stardust_springboot.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Rewrites path-style RPC verbs written with a colon into a normal sub-path so they match Spring
 * MVC controller mappings. The codebase exposes actions as {@code /messages:stream},
 * {@code /messages/{id}:regenerate} and {@code /admin/users/{id}/usage:adjust}; the controllers are
 * mapped to the equivalent {@code /.../stream}, {@code /.../{id}/regenerate} and
 * {@code /.../usage/adjust} paths. Rewriting the request URI keeps every caller using the colon
 * convention while the controllers stay plain REST resources.
 *
 * <p>Only the trailing {@code :verb} segment is rewritten, so normal paths (and query strings) are
 * untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ColonVerbRewriteFilter extends OncePerRequestFilter {
    private static final Pattern COLON_VERB = Pattern.compile("(.+):([a-z][a-z0-9_-]*)$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        java.util.regex.Matcher matcher = COLON_VERB.matcher(uri);
        if (matcher.matches()) {
            String rewritten = matcher.group(1) + "/" + matcher.group(2);
            chain.doFilter(new VerbRequestWrapper(request, rewritten), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /** Exposes the rewritten path to the servlet container and Spring MVC routing. */
    static final class VerbRequestWrapper extends HttpServletRequestWrapper {
        private final String path;

        VerbRequestWrapper(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return path;
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
