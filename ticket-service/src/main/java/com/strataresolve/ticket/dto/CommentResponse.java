package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a comment on a ticket.
 */
public record CommentResponse(
        UUID id,
        UUID ticketId,
        UUID authorId,
        String content,
        CommentVisibility visibility,
        Instant createdAt
) {
    /**
     * Creates a CommentResponse from a Comment entity.
     */
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getVisibility(),
                comment.getCreatedAt()
        );
    }
}
