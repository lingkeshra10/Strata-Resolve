package com.strataresolve.reporting.dto;

import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for an SLA compliance report.
 * Shows acknowledgement and resolution SLA compliance percentages,
 * optionally filtered by date range, category, and priority.
 *
 * <p>Validates: Requirements 15.2
 */
public record SlaComplianceReportResponse(
        Instant from,
        Instant to,
        Category categoryFilter,
        Priority priorityFilter,
        int totalTickets,
        int acknowledgementCompliant,
        int acknowledgementBreached,
        double acknowledgementCompliancePercent,
        int resolutionCompliant,
        int resolutionBreached,
        double resolutionCompliancePercent,
        List<CategoryBreakdown> categoryBreakdowns
) {

    /**
     * Breakdown of SLA compliance per category.
     */
    public record CategoryBreakdown(
            Category category,
            int totalTickets,
            double acknowledgementCompliancePercent,
            double resolutionCompliancePercent
    ) {}
}
