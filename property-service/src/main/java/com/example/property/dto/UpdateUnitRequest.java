package com.example.property.dto;

import com.example.property.domain.OccupancyStatus;
import com.example.property.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing unit.
 */
public record UpdateUnitRequest(
        @NotBlank(message = "Unit number is required")
        @Size(max = 50, message = "Unit number must not exceed 50 characters")
        String unitNumber,

        Integer floor,

        @NotNull(message = "Unit type is required")
        UnitType type,

        @NotNull(message = "Occupancy status is required")
        OccupancyStatus occupancyStatus
) {
}
