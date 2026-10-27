package com.spacepick;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance protection. Use a shared store or gateway before scaling out. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long expiresAt, int requests) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimitFilter() { this(Clock.systemUTC()); }
    AuthRateLimitFilter(Clock clock) { this.clock = clock; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") ||
            !(request.getServletPath().equals("/api/auth/login") || request.getServletPath().equals("/api/auth/signup"));
    }
    private synchronized long retryAfter(String key, int limit) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        Window window = windows.get(key);
        if (window == null) {
            // Bound memory; reject new buckets temporarily when saturated.
            if (windows.size() >= 10000) return 60;
            window = new Window(now + 60000, 0);
        }
        if (window.requests() >= limit) return Math.max(1, (window.expiresAt() - now + 999) / 1000);
        windows.put(key, new Window(window.expiresAt(), window.requests() + 1));
        return 0;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path = request.getServletPath();
        // Forwarded headers are intentionally not trusted by this local application.
        long retry = retryAfter(path + ":" + request.getRemoteAddr(),path.endsWith("/signup") ? 5 : 20);
        if (retry == 0) { chain.doFilter(request,response); return; }
        response.setStatus(429);
        response.setHeader("Retry-After",Long.toString(retry));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"AUTH_RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"retryAfter\":" + retry + "}");
    }
}