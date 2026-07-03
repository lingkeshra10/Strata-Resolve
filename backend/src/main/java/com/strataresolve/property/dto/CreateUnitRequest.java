package com.strataresolve.property.dto;

import com.strataresolve.property.domain.OccupancyStatus;
import com.strataresolve.property.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new unit within a block.
 */
public record CreateUnitRequest(
        @NotBlank(message = "Unit number is required")
        @Size(max = 50, message = "Unit number must not exceed 50 characters")
        String unitNumber,

        Integer floor,

        @NotNull(message = "Unit type is required")
        UnitType type,

        OccupancyStatus occupancyStatus
) {
}
