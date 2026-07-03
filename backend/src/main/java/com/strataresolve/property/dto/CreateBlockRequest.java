package com.strataresolve.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new block within a property.
 */
public record CreateBlockRequest(
        @NotBlank(message = "Block name is required")
        @Size(max = 255, message = "Block name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Block label must not exceed 255 characters")
        String label
) {
}
