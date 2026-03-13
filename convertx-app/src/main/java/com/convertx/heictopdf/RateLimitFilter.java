package com.convertx.heictopdf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private final ApplicationSecurityProperties properties;
    private final Map<String, RequestWindow> windows = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupTicker = new AtomicInteger();

    public RateLimitFilter(ApplicationSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRateLimit().isEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        cleanupExpiredWindows();

        String key = clientKey(request);
        long now = System.currentTimeMillis();
        RequestWindow window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStartedAt() >= WINDOW_MILLIS) {
                return new RequestWindow(now, new AtomicInteger(1));
            }
            existing.requestCount().incrementAndGet();
            return existing;
        });

        if (window.requestCount().get() > properties.getRateLimit().getRequestsPerMinute()) {
            response.setStatus(429);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("Too many requests. Wait a minute and try again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupExpiredWindows() {
        if (cleanupTicker.incrementAndGet() % 100 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAt() >= WINDOW_MILLIS);
    }

    private record RequestWindow(long windowStartedAt, AtomicInteger requestCount) {
    }
}
