package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a comment on a ticket.
 *
 * @param content    the comment text content
 * @param visibility whether the comment is PUBLIC or INTERNAL
 */
public record CreateCommentRequest(
        @NotBlank(message = "Comment content must not be blank")
        @Size(max = 5000, message = "Comment content must not exceed 5000 characters")
        String content,

        @NotNull(message = "Visibility must be specified")
        CommentVisibility visibility
) {
}
