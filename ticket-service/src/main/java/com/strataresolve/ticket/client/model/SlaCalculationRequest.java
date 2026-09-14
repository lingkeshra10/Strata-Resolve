package com.strataresolve.ticket.client.model;

import java.util.UUID;

public record SlaCalculationRequest(
        UUID propertyId,
        String timezone,
        String category,
        String priority
) {
}
