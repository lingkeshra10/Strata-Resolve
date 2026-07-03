package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse.CategoryBreakdown;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating SLA compliance reports.
 * Computes acknowledgement and resolution SLA compliance percentages,
 * filtered by date range, category, and priority.
 *
 * <p>Validates: Requirements 15.2
 * <p>SLA compliance = percentage of tickets where acknowledgement/resolution
 * was within the target deadline.
 */
@Service
@Transactional(readOnly = true)
public class SlaReportService {

    private final TicketRepository ticketRepository;

    public SlaReportService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generates an SLA compliance report for a property, optionally filtered by
     * date range, category, and priority.
     *
     * @param propertyId the property to report on
     * @param from       start of date range (inclusive), nullable
     * @param to         end of date range (inclusive), nullable
     * @param category   category filter, nullable
     * @param priority   priority filter, nullable
     * @return the SLA compliance report
     */
    public SlaComplianceReportResponse generateReport(UUID propertyId, Instant from, Instant to,
                                                       Category category, Priority priority) {
        List<Ticket> allTickets = ticketRepository.findByPropertyId(propertyId);

        // Filter by date range
        List<Ticket> filtered = allTickets.stream()
                .filter(t -> from == null || !t.getCreatedAt().isBefore(from))
                .filter(t -> to == null || !t.getCreatedAt().isAfter(to))
                .filter(t -> category == null || t.getCategory() == category)
                .filter(t -> priority == null || t.getPriority() == priority)
                .toList();

        // Only consider tickets that have SLA targets (acknowledgementDueAt set)
        List<Ticket> ticketsWithSla = filtered.stream()
                .filter(t -> t.getAcknowledgementDueAt() != null || t.getResolutionDueAt() != null)
                .toList();

        int totalTickets = ticketsWithSla.size();

        int ackCompliant = 0;
        int ackBreached = 0;
        int resCompliant = 0;
        int resBreached = 0;

        for (Ticket ticket : ticketsWithSla) {
            if (ticket.getAcknowledgementDueAt() != null) {
                if (isAcknowledgementCompliant(ticket)) {
                    ackCompliant++;
                } else {
                    ackBreached++;
                }
            }

            if (ticket.getResolutionDueAt() != null) {
                if (isResolutionCompliant(ticket)) {
                    resCompliant++;
                } else {
                    resBreached++;
                }
            }
        }

        double ackCompliancePercent = computePercentage(ackCompliant, ackCompliant + ackBreached);
        double resCompliancePercent = computePercentage(resCompliant, resCompliant + resBreached);

        // Generate category breakdowns
        List<CategoryBreakdown> categoryBreakdowns = generateCategoryBreakdowns(ticketsWithSla);

        return new SlaComplianceReportResponse(
                from, to, category, priority,
                totalTickets,
                ackCompliant, ackBreached, ackCompliancePercent,
                resCompliant, resBreached, resCompliancePercent,
                categoryBreakdowns
        );
    }

    /**
     * Determines if acknowledgement SLA was met for a ticket.
     * A ticket is considered compliant if it was acknowledged before or at
     * the acknowledgement deadline.
     * If not yet acknowledged but still within deadline, it's considered on-track (compliant).
     * If not yet acknowledged and past deadline, it's breached.
     */
    boolean isAcknowledgementCompliant(Ticket ticket) {
        if (ticket.getAcknowledgementDueAt() == null) {
            return true;
        }
        if (ticket.getAcknowledgedAt() != null) {
            return !ticket.getAcknowledgedAt().isAfter(ticket.getAcknowledgementDueAt());
        }
        // Not yet acknowledged — check SLA status for breach
        SlaStatus status = ticket.getSlaStatus();
        return status != SlaStatus.ACK_BREACHED && status != SlaStatus.BOTH_BREACHED;
    }

    /**
     * Determines if resolution SLA was met for a ticket.
     * A ticket is considered compliant if it was resolved before or at the resolution deadline.
     * If not yet resolved but still within deadline, it's considered on-track (compliant).
     * If not yet resolved and past deadline, it's breached.
     */
    boolean isResolutionCompliant(Ticket ticket) {
        if (ticket.getResolutionDueAt() == null) {
            return true;
        }
        if (ticket.getResolvedAt() != null) {
            return !ticket.getResolvedAt().isAfter(ticket.getResolutionDueAt());
        }
        // Not yet resolved — check SLA status for breach
        SlaStatus status = ticket.getSlaStatus();
        return status != SlaStatus.RESOLUTION_BREACHED && status != SlaStatus.BOTH_BREACHED;
    }

    private List<CategoryBreakdown> generateCategoryBreakdowns(List<Ticket> tickets) {
        Map<Category, List<Ticket>> byCategory = tickets.stream()
                .collect(Collectors.groupingBy(Ticket::getCategory, () -> new EnumMap<>(Category.class), Collectors.toList()));

        List<CategoryBreakdown> breakdowns = new ArrayList<>();
        for (Map.Entry<Category, List<Ticket>> entry : byCategory.entrySet()) {
            List<Ticket> catTickets = entry.getValue();
            int catTotal = catTickets.size();

            int catAckCompliant = 0;
            int catAckTotal = 0;
            int catResCompliant = 0;
            int catResTotal = 0;

            for (Ticket t : catTickets) {
                if (t.getAcknowledgementDueAt() != null) {
                    catAckTotal++;
                    if (isAcknowledgementCompliant(t)) {
                        catAckCompliant++;
                    }
                }
                if (t.getResolutionDueAt() != null) {
                    catResTotal++;
                    if (isResolutionCompliant(t)) {
                        catResCompliant++;
                    }
                }
            }

            breakdowns.add(new CategoryBreakdown(
                    entry.getKey(),
                    catTotal,
                    computePercentage(catAckCompliant, catAckTotal),
                    computePercentage(catResCompliant, catResTotal)
            ));
        }

        return breakdowns;
    }

    /**
     * Computes a percentage value, returning 100.0 when there are no applicable tickets.
     */
    static double computePercentage(int compliant, int total) {
        if (total == 0) {
            return 100.0;
        }
        return (compliant * 100.0) / total;
    }
}
