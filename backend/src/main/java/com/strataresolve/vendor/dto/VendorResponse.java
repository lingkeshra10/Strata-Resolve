package com.strataresolve.vendor.dto;

import com.strataresolve.vendor.domain.Vendor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a vendor associated with a property.
 */
public record VendorResponse(
        UUID id,
        UUID propertyId,
        String name,
        String contactEmail,
        String contactPhone,
        boolean isActive,
        Instant createdAt
) {
    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getPropertyId(),
                vendor.getName(),
                vendor.getContactEmail(),
                vendor.getContactPhone(),
                vendor.isActive(),
                vendor.getCreatedAt()
        );
    }
}
