package com.omnichannel.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple fixed-window, per-client-IP limiter (in memory, single gateway instance).
 * Replace with a Redis based limiter if the gateway is scaled horizontally.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long startedAt, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimitFilter(@Value("${gateway.rate-limit.requests-per-minute:120}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Nginx terminates TLS and sets X-Real-IP; the gateway is only reachable via 127.0.0.1
        String ip = request.getHeader("X-Real-IP");
        String key = ip != null ? ip : request.getRemoteAddr();
        long now = System.currentTimeMillis();

        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.startedAt() >= WINDOW_MS ? new Window(now, new AtomicInteger()) : old);

        if (w.count().incrementAndGet() > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":1005,\"message\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
