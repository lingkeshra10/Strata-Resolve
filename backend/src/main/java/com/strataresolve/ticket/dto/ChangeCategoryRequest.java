package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Category;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for changing a ticket's category.
 * Restricted to Property Managers.
 *
 * @param category the new category for the ticket
 */
public record ChangeCategoryRequest(
        @NotNull(message = "Category is required")
        Category category
) {
}
