package com.strataresolve.shared.tenant;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.shared.exception.AuthenticationRequiredException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantContextFilter.
 */
@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @Mock
    private MembershipCheckRepository membershipCheckRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Filter hibernateFilter;

    @Mock
    private FilterChain filterChain;

    private TenantContextFilter tenantContextFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tenantContextFilter = new TenantContextFilter(membershipCheckRepository, entityManager);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void doFilter_passesThrough_whenNoPropertyIdPresent() throws ServletException, IOException {
        request.setRequestURI("/api/tickets");
        setAuthenticatedUser(UUID.randomUUID());

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void doFilter_extractsPropertyId_fromPathParameter() throws ServletException, IOException {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + propertyId + "/tickets");
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, propertyId)).thenReturn(true);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantContextFilter.TENANT_FILTER_NAME)).thenReturn(hibernateFilter);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(session).enableFilter(TenantContextFilter.TENANT_FILTER_NAME);
        verify(hibernateFilter).setParameter("propertyId", propertyId.toString());
    }

    @Test
    void doFilter_extractsPropertyId_fromHeader() throws ServletException, IOException {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/tickets");
        request.addHeader(TenantContextFilter.PROPERTY_ID_HEADER, propertyId.toString());
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, propertyId)).thenReturn(true);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantContextFilter.TENANT_FILTER_NAME)).thenReturn(hibernateFilter);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(membershipCheckRepository).hasActiveMembership(userId, propertyId);
    }

    @Test
    void doFilter_prefersPathParameter_overHeader() throws ServletException, IOException {
        UUID pathPropertyId = UUID.randomUUID();
        UUID headerPropertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + pathPropertyId + "/blocks");
        request.addHeader(TenantContextFilter.PROPERTY_ID_HEADER, headerPropertyId.toString());
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, pathPropertyId)).thenReturn(true);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantContextFilter.TENANT_FILTER_NAME)).thenReturn(hibernateFilter);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(membershipCheckRepository).hasActiveMembership(userId, pathPropertyId);
        verify(membershipCheckRepository, never()).hasActiveMembership(userId, headerPropertyId);
    }

    @Test
    void doFilter_throwsAuthenticationRequired_whenNoAuthentication() {
        UUID propertyId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + propertyId + "/tickets");
        // No authentication set

        assertThatThrownBy(() ->
                tenantContextFilter.doFilterInternal(request, response, filterChain)
        ).isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void doFilter_throwsAccessDenied_whenNoActiveMembership() {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + propertyId + "/tickets");
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, propertyId)).thenReturn(false);

        assertThatThrownBy(() ->
                tenantContextFilter.doFilterInternal(request, response, filterChain)
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doFilter_clearsContext_afterSuccessfulRequest() throws ServletException, IOException {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + propertyId + "/tickets");
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, propertyId)).thenReturn(true);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantContextFilter.TENANT_FILTER_NAME)).thenReturn(hibernateFilter);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        // After doFilter completes, context should be cleared
        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void doFilter_clearsContext_onException() {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        request.setRequestURI("/api/properties/" + propertyId + "/tickets");
        setAuthenticatedUser(userId);

        when(membershipCheckRepository.hasActiveMembership(userId, propertyId)).thenReturn(false);

        try {
            tenantContextFilter.doFilterInternal(request, response, filterChain);
        } catch (Exception e) {
            // expected
        }

        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void shouldNotFilter_returnsTrue_forAuthEndpoints() {
        request.setRequestURI("/api/auth/login");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isTrue();

        request.setRequestURI("/api/auth/register");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isTrue();

        request.setRequestURI("/api/auth/refresh");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsTrue_forActuatorEndpoints() {
        request.setRequestURI("/actuator/health");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forApiEndpoints() {
        request.setRequestURI("/api/tickets");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isFalse();

        request.setRequestURI("/api/properties/" + UUID.randomUUID() + "/blocks");
        assertThat(tenantContextFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilter_passesThrough_whenHeaderContainsInvalidUuid() throws ServletException, IOException {
        request.setRequestURI("/api/tickets");
        request.addHeader(TenantContextFilter.PROPERTY_ID_HEADER, "not-a-uuid");
        setAuthenticatedUser(UUID.randomUUID());

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // No property context set — invalid UUID treated as missing
        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void extractPropertyId_returnsNull_whenPathDoesNotMatch() {
        request.setRequestURI("/api/users/profile");
        UUID result = tenantContextFilter.extractPropertyId(request);
        assertThat(result).isNull();
    }

    private void setAuthenticatedUser(UUID userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
