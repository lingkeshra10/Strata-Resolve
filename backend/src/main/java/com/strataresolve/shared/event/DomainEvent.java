package com.strataresolve.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the StrataResolve platform.
 * Carries common context: timestamp, acting user, and property context.
 */
public abstract class DomainEvent {

    private final Instant timestamp;
    private final UUID actingUserId;
    private final UUID propertyId;

    protected DomainEvent(UUID actingUserId, UUID propertyId) {
        this.timestamp = Instant.now();
        this.actingUserId = actingUserId;
        this.propertyId = propertyId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public UUID getActingUserId() {
        return actingUserId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }
}
