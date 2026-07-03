package com.strataresolve.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new property.
 */
public record CreatePropertyRequest(
        @NotBlank(message = "Property name is required")
        @Size(max = 255, message = "Property name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Property code is required")
        @Size(max = 50, message = "Property code must not exceed 50 characters")
        String code,

        @NotBlank(message = "Address is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address,

        @NotBlank(message = "Timezone is required")
        @Size(max = 100, message = "Timezone must not exceed 100 characters")
        String timezone
) {
}
