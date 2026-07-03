package com.strataresolve.shared.tenant;

import jakarta.persistence.EntityManager;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuration that registers the {@link TenantContextFilter} as a servlet filter.
 *
 * <p>The filter is registered with a high order value so that it runs after
 * Spring Security's filter chain (which handles JWT authentication).
 * This ensures the authenticated user is available in the SecurityContext
 * when the TenantContextFilter validates membership.
 */
@Configuration
public class TenantFilterConfiguration {

    /**
     * The filter order is set after Spring Security (typically at order -100).
     * We use order 10 to ensure security filters have completed authentication.
     */
    private static final int TENANT_FILTER_ORDER = 10;

    @Bean
    public TenantContextFilter tenantContextFilter(MembershipCheckRepository membershipCheckRepository,
                                                    EntityManager entityManager) {
        return new TenantContextFilter(membershipCheckRepository, entityManager);
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            TenantContextFilter tenantContextFilter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantContextFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(TENANT_FILTER_ORDER);
        registration.setName("tenantContextFilter");
        return registration;
    }
}
