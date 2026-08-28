package com.example.property.dto;

import com.example.property.domain.Property;
import com.example.property.domain.PropertyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a property.
 */
public record PropertyResponse(
        UUID id,
        String name,
        String code,
        String address,
        String timezone,
        PropertyStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getCode(),
                property.getAddress(),
                property.getTimezone(),
                property.getStatus(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}
