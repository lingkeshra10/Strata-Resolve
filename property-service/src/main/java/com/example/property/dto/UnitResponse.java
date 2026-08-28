package com.example.property.dto;

import com.example.property.domain.OccupancyStatus;
import com.example.property.domain.Unit;
import com.example.property.domain.UnitType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a unit within a block.
 */
public record UnitResponse(
        UUID id,
        UUID blockId,
        UUID propertyId,
        String unitNumber,
        Integer floor,
        UnitType type,
        OccupancyStatus occupancyStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getBlockId(),
                unit.getPropertyId(),
                unit.getUnitNumber(),
                unit.getFloor(),
                unit.getType(),
                unit.getOccupancyStatus(),
                unit.getCreatedAt(),
                unit.getUpdatedAt()
        );
    }
}
