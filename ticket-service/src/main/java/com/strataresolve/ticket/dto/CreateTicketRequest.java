package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting a new maintenance ticket.
 * The resident provides title, description, category, location, and an optional suggested priority.
 */
public record CreateTicketRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Category is required")
        Category category,

        @Size(max = 500, message = "Location must not exceed 500 characters")
        String location,

        Priority suggestedPriority
) {
}
