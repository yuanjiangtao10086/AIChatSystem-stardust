package com.example.stardust_springboot.common.web;

import com.example.stardust_springboot.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves the originating client address for rate limiting and audit.
 *
 * <p>By default only the direct peer ({@code getRemoteAddr()}) is trusted — a spoofed
 * {@code X-Forwarded-For} is ignored, so a client cannot shift blame onto another address. When the
 * direct peer matches {@code app.security.rate-limit.trusted-proxies}, the leftmost {@code X-Forwarded-For}
 * hop (the original client) is used instead.
 */
@Component
public class ClientIpResolver {
    private final List<String> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxies = properties.trustedProxies() == null ? List.of() : properties.trustedProxies();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isTrusted(remote) && StringUtils.hasText(request.getHeader("X-Forwarded-For"))) {
            String[] hops = request.getHeader("X-Forwarded-For").split(",");
            String client = hops[0].trim();
            if (StringUtils.hasText(client)) {
                return client;
            }
        }
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    private boolean isTrusted(String ip) {
        if (trustedProxies.isEmpty() || !StringUtils.hasText(ip)) {
            return false;
        }
        return trustedProxies.stream()
                .filter(StringUtils::hasText)
                .anyMatch(pattern -> pattern.equals(ip) || isInCidr(pattern, ip));
    }

    private boolean isInCidr(String cidr, String ip) {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            return false;
        }
        try {
            String base = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] baseBytes = InetAddress.getByName(base).getAddress();
            if (ipBytes.length != baseBytes.length || prefix < 0 || prefix > ipBytes.length * 8) {
                return false;
            }
            int bits = prefix;
            for (int i = 0; i < ipBytes.length && bits > 0; i++) {
                int mask = (bits >= 8) ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
                if ((ipBytes[i] & mask) != (baseBytes[i] & mask)) {
                    return false;
                }
                bits -= 8;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException ex) {
            return false;
        }
    }
}
