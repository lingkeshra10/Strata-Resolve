package com.strataresolve.ticket.client.model;

import java.util.UUID;

public record Property(
        UUID id,
        String name,
        String timezone,
        boolean active
) {
}
