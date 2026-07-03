package com.strataresolve.sla.service;

import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled task that periodically checks for SLA breaches on active tickets.
 *
 * <p>Runs at a configurable interval (default: every 60 seconds) and detects tickets
 * where acknowledgement or resolution deadlines have been exceeded without the
 * corresponding action being taken.
 *
 * <p>A ticket is considered:
 * <ul>
 *   <li>{@link SlaStatus#ACK_BREACHED} if acknowledgement_due_at < now AND acknowledged_at IS NULL</li>
 *   <li>{@link SlaStatus#RESOLUTION_BREACHED} if resolution_due_at < now AND resolved_at IS NULL</li>
 *   <li>{@link SlaStatus#BOTH_BREACHED} if both conditions are met</li>
 * </ul>
 *
 * <p>Only tickets in non-terminal statuses (not CLOSED, CANCELLED, REJECTED, RESOLVED)
 * are evaluated.
 *
 * <p>Validates: Requirement 14.3
 */
@Component
public class SlaMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorScheduler.class);

    private final TicketRepository ticketRepository;

    public SlaMonitorScheduler(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Periodically polls for tickets with breached SLA targets and updates their sla_status.
     * The interval is configurable via {@code app.sla.monitor.poll-interval-ms}.
     */
    @Scheduled(fixedDelayString = "${app.sla.monitor.poll-interval-ms:60000}")
    @Transactional
    public void detectBreaches() {
        Instant now = Instant.now();
        detectBreachesAtTime(now);
    }

    /**
     * Core breach detection logic, separated from the scheduled method for testability.
     * Finds all tickets with breached SLA targets and updates their sla_status accordingly.
     *
     * @param now the reference time for breach evaluation
     * @return the number of tickets updated
     */
    @Transactional
    public int detectBreachesAtTime(Instant now) {
        List<Ticket> breachableTickets = ticketRepository.findTicketsWithBreachedSla(now);

        if (breachableTickets.isEmpty()) {
            log.debug("SLA breach check: no breaches detected at {}", now);
            return 0;
        }

        int updatedCount = 0;
        for (Ticket ticket : breachableTickets) {
            SlaStatus newStatus = determineSlaStatus(ticket, now);
            if (newStatus != ticket.getSlaStatus()) {
                log.info("SLA breach detected for ticket {} (ref: {}): {} -> {}",
                        ticket.getId(), ticket.getReferenceNumber(),
                        ticket.getSlaStatus(), newStatus);
                ticket.setSlaStatus(newStatus);
                ticketRepository.save(ticket);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            log.info("SLA breach check completed: {} ticket(s) updated", updatedCount);
        }

        return updatedCount;
    }

    /**
     * Determines the correct SLA status for a ticket based on current time.
     *
     * <p>Logic:
     * <ul>
     *   <li>If both ack and resolution are breached → BOTH_BREACHED</li>
     *   <li>If only ack is breached → ACK_BREACHED</li>
     *   <li>If only resolution is breached → RESOLUTION_BREACHED</li>
     *   <li>Otherwise → ON_TRACK (should not happen given the query filter)</li>
     * </ul>
     *
     * @param ticket the ticket to evaluate
     * @param now    the reference time
     * @return the determined SLA status
     */
    public SlaStatus determineSlaStatus(Ticket ticket, Instant now) {
        boolean ackBreached = isAcknowledgementBreached(ticket, now);
        boolean resBreached = isResolutionBreached(ticket, now);

        if (ackBreached && resBreached) {
            return SlaStatus.BOTH_BREACHED;
        } else if (ackBreached) {
            return SlaStatus.ACK_BREACHED;
        } else if (resBreached) {
            return SlaStatus.RESOLUTION_BREACHED;
        }

        return SlaStatus.ON_TRACK;
    }

    /**
     * Checks if the acknowledgement SLA has been breached.
     * A breach occurs when acknowledgement_due_at is before now and the ticket
     * has not been acknowledged (acknowledged_at is null).
     */
    private boolean isAcknowledgementBreached(Ticket ticket, Instant now) {
        return ticket.getAcknowledgementDueAt() != null
                && ticket.getAcknowledgementDueAt().isBefore(now)
                && ticket.getAcknowledgedAt() == null;
    }

    /**
     * Checks if the resolution SLA has been breached.
     * A breach occurs when resolution_due_at is before now and the ticket
     * has not been resolved (resolved_at is null).
     */
    private boolean isResolutionBreached(Ticket ticket, Instant now) {
        return ticket.getResolutionDueAt() != null
                && ticket.getResolutionDueAt().isBefore(now)
                && ticket.getResolvedAt() == null;
    }
}
