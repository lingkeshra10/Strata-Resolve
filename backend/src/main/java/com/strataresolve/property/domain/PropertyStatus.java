package com.strataresolve.property.domain;

/**
 * Represents the operational status of a property in the platform.
 * An INACTIVE property prevents new ticket submissions while preserving existing data.
 */
public enum PropertyStatus {
    ACTIVE,
    INACTIVE
}
