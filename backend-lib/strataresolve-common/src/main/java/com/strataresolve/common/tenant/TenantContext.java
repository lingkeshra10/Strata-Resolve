package com.strataresolve.common.tenant;

import java.util.UUID;

/**
 * Holds the current property_id in a ThreadLocal for multi-tenancy isolation.
 * Set by TenantContextFilter at the beginning of each request and cleared on completion.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_PROPERTY_ID = new ThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    /**
     * Returns the property_id for the current request context.
     *
     * @return the current property_id, or null if not set
     */
    public static UUID getCurrentPropertyId() {
        return CURRENT_PROPERTY_ID.get();
    }

    /**
     * Sets the property_id for the current request context.
     *
     * @param propertyId the property UUID to set
     */
    public static void setCurrentPropertyId(UUID propertyId) {
        CURRENT_PROPERTY_ID.set(propertyId);
    }

    /**
     * Clears the current tenant context. Must be called on response completion
     * to prevent ThreadLocal leaks in pooled thread environments.
     */
    public static void clear() {
        CURRENT_PROPERTY_ID.remove();
    }

    /**
     * Returns true if a property context is currently set.
     */
    public static boolean isSet() {
        return CURRENT_PROPERTY_ID.get() != null;
    }
}
