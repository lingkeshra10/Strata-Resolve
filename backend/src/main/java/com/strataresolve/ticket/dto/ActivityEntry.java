package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.ActivityType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO representing a single entry in a ticket's activity history.
 * Provides a unified view of all ticket activities (status changes, comments, assignments, attachment uploads)
 * in a consistent format suitable for chronological display.
 *
 * <p>Every activity entry guarantees non-null {@code actorId} and {@code timestamp}.
 *
 * @param type        the type of activity (STATUS_CHANGE, COMMENT, ASSIGNMENT, ATTACHMENT_UPLOAD)
 * @param timestamp   when the activity occurred (never null)
 * @param actorId     the user who performed the action (never null)
 * @param description a human-readable summary of the activity
 * @param metadata    additional details specific to the activity type
 */
public record ActivityEntry(
        ActivityType type,
        Instant timestamp,
        UUID actorId,
        String description,
        Map<String, Object> metadata
) {

    /**
     * Validates that actorId and timestamp are non-null at construction time.
     */
    public ActivityEntry {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
