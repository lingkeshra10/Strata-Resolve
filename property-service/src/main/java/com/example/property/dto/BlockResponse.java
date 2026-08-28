package com.example.property.dto;

import com.example.property.domain.Block;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a block within a property.
 */
public record BlockResponse(
        UUID id,
        UUID propertyId,
        String name,
        String label,
        Instant createdAt,
        Instant updatedAt
) {
    public static BlockResponse from(Block block) {
        return new BlockResponse(
                block.getId(),
                block.getPropertyId(),
                block.getName(),
                block.getLabel(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }
}
