package com.example.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing block.
 */
public record UpdateBlockRequest(
        @NotBlank(message = "Block name is required")
        @Size(max = 255, message = "Block name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Block label must not exceed 255 characters")
        String label
) {
}
