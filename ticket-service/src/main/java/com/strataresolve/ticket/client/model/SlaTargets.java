package com.strataresolve.ticket.client.model;

import java.time.Instant;

public record SlaTargets(
        Instant acknowledgementDueAt,
        Instant resolutionDueAt
) {
}
