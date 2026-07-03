package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.AgeBracket;
import com.strataresolve.reporting.dto.AgeingBracketEntry;
import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for generating the ticket ageing report.
 *
 * <p>Generates a report showing open tickets grouped by age brackets:
 * 0-3 days, 4-7 days, 8-14 days, 15-30 days, and over 30 days.
 *
 * <p>Access is restricted to Property Managers and Committee Members
 * with active Membership for the target Property.
 *
 * <p>Open tickets are those NOT in terminal status (CLOSED, CANCELLED, REJECTED).
 * Age is calculated as days since ticket.created_at.
 */
@Service
public class AgeingReportService {

    private static final Set<TicketStatus> TERMINAL_STATUSES = EnumSet.of(
            TicketStatus.CLOSED,
            TicketStatus.CANCELLED,
            TicketStatus.REJECTED
    );

    private static final Set<Role> ALLOWED_ROLES = EnumSet.of(
            Role.PROPERTY_MANAGER,
            Role.COMMITTEE_MEMBER
    );

    private final TicketRepository ticketRepository;
    private final MembershipRepository membershipRepository;

    public AgeingReportService(TicketRepository ticketRepository,
                               MembershipRepository membershipRepository) {
        this.ticketRepository = ticketRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Generates the ageing report for the specified property.
     *
     * @param propertyId the ID of the property to generate the report for
     * @param userId     the ID of the requesting user
     * @return the ageing report response with tickets grouped by age brackets
     * @throws AccessDeniedException if the user does not have PROPERTY_MANAGER or
     *                               COMMITTEE_MEMBER role with active membership for the property
     */
    @Transactional(readOnly = true)
    public AgeingReportResponse generateReport(UUID propertyId, UUID userId) {
        validateAccess(propertyId, userId);

        Instant now = Instant.now();
        List<Ticket> allTickets = ticketRepository.findByPropertyId(propertyId);

        // Filter to open tickets only (not in terminal status)
        List<Ticket> openTickets = allTickets.stream()
                .filter(ticket -> !TERMINAL_STATUSES.contains(ticket.getStatus()))
                .toList();

        return buildReport(propertyId, openTickets, now);
    }

    /**
     * Generates the ageing report using a specified "now" time.
     * This overload is useful for testing to control the reference time.
     *
     * @param propertyId  the property ID
     * @param userId      the requesting user ID
     * @param referenceTime the time to use as "now" for age calculation
     * @return the ageing report response
     */
    @Transactional(readOnly = true)
    public AgeingReportResponse generateReport(UUID propertyId, UUID userId, Instant referenceTime) {
        validateAccess(propertyId, userId);

        List<Ticket> allTickets = ticketRepository.findByPropertyId(propertyId);

        List<Ticket> openTickets = allTickets.stream()
                .filter(ticket -> !TERMINAL_STATUSES.contains(ticket.getStatus()))
                .toList();

        return buildReport(propertyId, openTickets, referenceTime);
    }

    /**
     * Validates that the requesting user has an allowed role (PROPERTY_MANAGER or
     * COMMITTEE_MEMBER) with active membership for the target property.
     */
    private void validateAccess(UUID propertyId, UUID userId) {
        List<Membership> activeMemberships = membershipRepository
                .findActiveByUserIdAndPropertyId(userId, propertyId);

        boolean hasAllowedRole = activeMemberships.stream()
                .anyMatch(m -> ALLOWED_ROLES.contains(m.getRole()));

        if (!hasAllowedRole) {
            throw new AccessDeniedException(
                    "User does not have PROPERTY_MANAGER or COMMITTEE_MEMBER role for this property");
        }
    }

    /**
     * Builds the ageing report by grouping open tickets into age brackets.
     */
    private AgeingReportResponse buildReport(UUID propertyId, List<Ticket> openTickets, Instant referenceTime) {
        Map<AgeBracket, List<UUID>> bracketMap = new EnumMap<>(AgeBracket.class);
        for (AgeBracket bracket : AgeBracket.values()) {
            bracketMap.put(bracket, new ArrayList<>());
        }

        for (Ticket ticket : openTickets) {
            long ageDays = calculateAgeDays(ticket.getCreatedAt(), referenceTime);
            AgeBracket bracket = AgeBracket.fromDays(ageDays);
            bracketMap.get(bracket).add(ticket.getId());
        }

        List<AgeingBracketEntry> entries = new ArrayList<>();
        for (AgeBracket bracket : AgeBracket.values()) {
            entries.add(AgeingBracketEntry.of(bracket, bracketMap.get(bracket)));
        }

        return new AgeingReportResponse(propertyId, referenceTime, openTickets.size(), entries);
    }

    /**
     * Calculates the age in days between the ticket creation time and the reference time.
     * Uses floor division (truncates toward zero), so a ticket created less than 24h ago is 0 days old.
     */
    static long calculateAgeDays(Instant createdAt, Instant referenceTime) {
        Duration duration = Duration.between(createdAt, referenceTime);
        return duration.toDays();
    }
}
