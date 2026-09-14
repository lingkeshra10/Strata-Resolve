package com.strataresolve.ticket.client.model;

import java.time.LocalDate;
import java.util.UUID;

public record Membership(
        UUID membershipId,
        UUID userId,
        UUID propertyId,
        UUID unitId,
        UUID vendorId,
        String role,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
