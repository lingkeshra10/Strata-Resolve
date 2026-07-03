/**
 * Shared multi-tenancy infrastructure for the StrataResolve platform.
 *
 * <p>Provides request-scoped property context via ThreadLocal, membership validation,
 * and automatic Hibernate filter enablement for data isolation.
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link com.strataresolve.shared.tenant.TenantContext} - ThreadLocal holder for property_id</li>
 *   <li>{@link com.strataresolve.shared.tenant.TenantContextFilter} - Servlet filter managing tenant lifecycle</li>
 *   <li>{@link com.strataresolve.shared.tenant.TenantAwareEntity} - Base entity with Hibernate filter definition</li>
 *   <li>{@link com.strataresolve.shared.tenant.MembershipCheckRepository} - Membership validation interface</li>
 * </ul>
 */
package com.strataresolve.shared.tenant;
