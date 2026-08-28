package com.strataresolve.notification.model;

import java.util.UUID;

public record NotificationRecipient(
        UUID userId,
        String email
) {
}
