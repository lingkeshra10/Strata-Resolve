package com.strataresolve.ticket.domain;

/**
 * Defines the visibility level of a comment on a ticket.
 * <ul>
 *   <li>{@code PUBLIC} - Visible to all users with access to the ticket, including residents</li>
 *   <li>{@code INTERNAL} - Visible only to management and assigned staff (Property Managers, Technicians, Vendor Technicians)</li>
 * </ul>
 */
public enum CommentVisibility {
    PUBLIC,
    INTERNAL
}
