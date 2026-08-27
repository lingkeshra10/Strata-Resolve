package com.strataresolve.common.tenant;

import com.strataresolve.common.exception.AccessDeniedException;
import com.strataresolve.common.exception.AuthenticationRequiredException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that establishes multi-tenancy context for each request.
 *
 * <p>Flow:
 * <ol>
 *   <li>Extracts property_id from the request path parameter or {@code X-Property-Id} header</li>
 *   <li>Validates the authenticated user holds an active membership for that property</li>
 *   <li>Stores property_id in {@link TenantContext} ThreadLocal</li>
 *   <li>Enables the Hibernate {@code tenantFilter} with the property_id parameter</li>
 *   <li>Clears context on response completion (in finally block)</li>
 * </ol>
 *
 * <p>Requests that don't target a specific property (e.g., auth endpoints, health checks)
 * pass through without tenant context being set.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    public static final String PROPERTY_ID_HEADER = "X-Property-Id";
    public static final String TENANT_FILTER_NAME = "tenantFilter";

    /**
     * Pattern matching /api/properties/{uuid}/... path segments.
     * Captures the UUID from the URL path.
     */
    private static final Pattern PROPERTY_PATH_PATTERN =
            Pattern.compile("/api/properties/([0-9a-fA-F\\-]{36})(?:/.*)?");

    private final MembershipCheckRepository membershipCheckRepository;
    private final EntityManager entityManager;

    public TenantContextFilter(MembershipCheckRepository membershipCheckRepository,
                               EntityManager entityManager) {
        this.membershipCheckRepository = membershipCheckRepository;
        this.entityManager = entityManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            UUID propertyId = extractPropertyId(request);

            if (propertyId != null) {
                // Require authentication when property context is requested
                UUID userId = extractAuthenticatedUserId();
                if (userId == null) {
                    throw new AuthenticationRequiredException(
                            "Authentication is required to access property resources");
                }

                // Validate membership
                if (!membershipCheckRepository.hasActiveMembership(userId, propertyId)) {
                    throw new AccessDeniedException(
                            "You do not have an active membership for this property");
                }

                // Set tenant context
                TenantContext.setCurrentPropertyId(propertyId);

                // Enable Hibernate tenant filter for this session
                enableHibernateFilter(propertyId);

                log.debug("Tenant context set: propertyId={}, userId={}", propertyId, userId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear context to prevent ThreadLocal leaks
            TenantContext.clear();
        }
    }

    /**
     * Determines whether this filter should be skipped for the given request.
     * Skips public endpoints that don't require tenant context.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/")
                || path.equals("/api/health");
    }

    /**
     * Extracts property_id from the request. Checks path parameter first,
     * then falls back to X-Property-Id header.
     *
     * @return the property UUID, or null if not present in the request
     */
    UUID extractPropertyId(HttpServletRequest request) {
        // 1. Try path parameter
        String path = request.getRequestURI();
        Matcher matcher = PROPERTY_PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            return parseUuid(matcher.group(1));
        }

        // 2. Fall back to X-Property-Id header
        String headerValue = request.getHeader(PROPERTY_ID_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            return parseUuid(headerValue.trim());
        }

        return null;
    }

    /**
     * Extracts the authenticated user's UUID from the SecurityContext.
     * Returns null if no authentication is present.
     */
    UUID extractAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalStr && !"anonymousUser".equals(principalStr)) {
            return parseUuid(principalStr);
        }

        // Support principal objects that have a UUID name
        if (principal != null && !"anonymousUser".equals(principal.toString())) {
            return parseUuid(authentication.getName());
        }

        return null;
    }

    /**
     * Enables the Hibernate tenant filter for the current persistence context.
     */
    private void enableHibernateFilter(UUID propertyId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TENANT_FILTER_NAME)
                .setParameter("propertyId", propertyId.toString());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid UUID format: {}", value);
            return null;
        }
    }
}
