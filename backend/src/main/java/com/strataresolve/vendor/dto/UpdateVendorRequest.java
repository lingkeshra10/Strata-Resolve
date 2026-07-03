package com.strataresolve.vendor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing vendor's details.
 */
public record UpdateVendorRequest(
        @NotBlank(message = "Vendor name is required")
        @Size(max = 255, message = "Vendor name must not exceed 255 characters")
        String name,

        @Email(message = "Contact email must be a valid email address")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,

        @Size(max = 50, message = "Contact phone must not exceed 50 characters")
        String contactPhone
) {
}
