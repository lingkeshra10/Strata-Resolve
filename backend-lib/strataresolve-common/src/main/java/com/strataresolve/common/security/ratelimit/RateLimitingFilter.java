package com.strataresolve.common.security.ratelimit;

import com.strataresolve.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final List<String> protectedPathPrefixes;

    public RateLimitingFilter(
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            List<String> protectedPathPrefixes
    ) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.protectedPathPrefixes =
                List.copyOf(protectedPathPrefixes);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = getClientIp(request);

        String key =
                clientIp + ":" +
                        request.getMethod() + ":" +
                        request.getServletPath();

        if (!rateLimiter.allowRequest(key)) {

            response.setStatus(
                    HttpStatus.TOO_MANY_REQUESTS.value()
            );

            response.setContentType(
                    MediaType.APPLICATION_JSON_VALUE
            );

            ErrorResponse errorResponse =
                    ErrorResponse.of(
                            HttpStatus.TOO_MANY_REQUESTS.value(),
                            HttpStatus.TOO_MANY_REQUESTS
                                    .getReasonPhrase(),
                            "Too many requests. Please try again later.",
                            "RATE_LIMITED"
                    );

            objectMapper.writeValue(
                    response.getWriter(),
                    errorResponse
            );

            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }

    @Override
    protected boolean shouldNotFilter(
            @NonNull HttpServletRequest request
    ) {

        String path = request.getServletPath();

        return protectedPathPrefixes
                .stream()
                .noneMatch(path::startsWith);
    }

    private String getClientIp(
            HttpServletRequest request
    ) {

        String forwardedFor =
                request.getHeader("X-Forwarded-For");

        if (forwardedFor != null &&
                !forwardedFor.isBlank()) {

            return forwardedFor
                    .split(",")[0]
                    .trim();
        }

        return request.getRemoteAddr();
    }
}
