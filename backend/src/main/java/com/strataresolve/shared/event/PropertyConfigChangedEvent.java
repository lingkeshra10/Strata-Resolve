package com.strataresolve.shared.event;

import java.util.UUID;

/**
 * Domain event published when a property, block, or unit configuration changes.
 * Used for audit trail on create, update, and deactivate operations in the property module.
 */
public class PropertyConfigChangedEvent extends DomainEvent {

    private final String entityType;
    private final UUID entityId;
    private final String action;
    private final String previousValue;
    private final String newValue;

    public PropertyConfigChangedEvent(UUID actingUserId, UUID propertyId,
                                      String entityType, UUID entityId,
                                      String action, String previousValue, String newValue) {
        super(actingUserId, propertyId);
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
