package com.strataresolve.identity.domain;

/**
 * Enumeration of all roles available in the platform.
 * A user's role within a property is defined by their Membership record.
 */
public enum Role {
    PLATFORM_ADMIN,
    PROPERTY_MANAGER,
    COMMITTEE_MEMBER,
    RESIDENT_OWNER,
    RESIDENT_TENANT,
    TECHNICIAN,
    VENDOR_ADMIN,
    VENDOR_TECHNICIAN
}
