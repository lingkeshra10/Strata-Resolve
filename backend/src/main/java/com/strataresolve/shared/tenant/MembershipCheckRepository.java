package com.strataresolve.shared.tenant;

import java.util.UUID;

/**
 * Minimal interface for checking active membership.
 * Used by TenantContextFilter to validate that the authenticated user
 * holds an active membership for the requested property.
 *
 * The full MembershipService/Repository will be built in the user module (task 3.5).
 * This interface is intentionally narrow to avoid coupling the filter to the full domain model.
 */
public interface MembershipCheckRepository {

    /**
     * Returns true if the given user has an active membership for the specified property.
     *
     * @param userId     the authenticated user's ID
     * @param propertyId the target property's ID
     * @return true if active membership exists
     */
    boolean hasActiveMembership(UUID userId, UUID propertyId);
}
