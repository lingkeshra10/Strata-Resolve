package com.strataresolve.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strataresolve.shared.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiting filter for authentication endpoints.
 * Uses a ConcurrentHashMap to track request counts per IP address.
 * Limits requests to prevent brute-force attacks on login/register/refresh endpoints.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_DURATION_MS = 60_000; // 1 minute

    private final Map<String, RateLimitEntry> requestCounts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String key = clientIp + ":" + request.getServletPath();

        RateLimitEntry entry = requestCounts.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart > WINDOW_DURATION_MS) {
                return new RateLimitEntry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (entry.count.get() > MAX_REQUESTS_PER_WINDOW) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = ErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many requests. Please try again later.",
                    "RATE_LIMITED"
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        // Only apply rate limiting on auth endpoints
        return !path.startsWith("/api/auth/");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
