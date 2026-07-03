package com.strataresolve.ticket.controller;

import com.strataresolve.shared.tenant.TenantContext;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.dto.ActivityEntry;
import com.strataresolve.ticket.dto.ChangeCategoryRequest;
import com.strataresolve.ticket.dto.ChangePriorityRequest;
import com.strataresolve.ticket.dto.CreateTicketRequest;
import com.strataresolve.ticket.dto.ReopenTicketRequest;
import com.strataresolve.ticket.dto.TicketResponse;
import com.strataresolve.ticket.dto.TransitionTicketStatusRequest;
import com.strataresolve.ticket.service.ActivityHistoryService;
import com.strataresolve.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for ticket operations.
 * Ticket submission is restricted to residents (RESIDENT_OWNER or RESIDENT_TENANT).
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final ActivityHistoryService activityHistoryService;

    public TicketController(TicketService ticketService, ActivityHistoryService activityHistoryService) {
        this.ticketService = ticketService;
        this.activityHistoryService = activityHistoryService;
    }

    /**
     * Submits a new maintenance ticket.
     * Restricted to residents with an active membership and linked unit in the current property context.
     *
     * @param request the ticket submission request
     * @param authentication the authenticated user
     * @return the created ticket with HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RESIDENT_OWNER', 'RESIDENT_TENANT')")
    public ResponseEntity<TicketResponse> submitTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {
        UUID submittedBy = (UUID) authentication.getPrincipal();
        UUID propertyId = TenantContext.getCurrentPropertyId();

        Ticket ticket = ticketService.submitTicket(request, propertyId, submittedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
    }

    /**
     * Retrieves a ticket by its ID.
     */
    @GetMapping("/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID ticketId) {
        Ticket ticket = ticketService.findById(ticketId);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Retrieves a ticket by its reference number.
     */
    @GetMapping("/reference/{referenceNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponse> getTicketByReference(@PathVariable String referenceNumber) {
        Ticket ticket = ticketService.findByReferenceNumber(referenceNumber);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Lists all tickets in the current property context.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TicketResponse>> listTickets() {
        UUID propertyId = TenantContext.getCurrentPropertyId();
        List<TicketResponse> tickets = ticketService.findByPropertyId(propertyId).stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    /**
     * Lists tickets visible to the authenticated resident.
     * Returns only tickets submitted by the resident or related to the resident's linked unit(s).
     * Enforces resident data scope per Requirements 21.1, 21.2, 21.3.
     *
     * @param authentication the authenticated resident user
     * @return list of tickets the resident is allowed to see
     */
    @GetMapping("/my-tickets")
    @PreAuthorize("hasAnyRole('RESIDENT_OWNER', 'RESIDENT_TENANT')")
    public ResponseEntity<List<TicketResponse>> listResidentTickets(Authentication authentication) {
        UUID residentId = (UUID) authentication.getPrincipal();
        UUID propertyId = TenantContext.getCurrentPropertyId();
        List<TicketResponse> tickets = ticketService.findResidentTickets(propertyId, residentId).stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    /**
     * Transitions a ticket's status to a new state.
     *
     * <p>Validates the transition against the workflow policy, records timestamps
     * for ACKNOWLEDGED and RESOLVED transitions, creates a StatusHistory entry,
     * and publishes a StatusChangedEvent.
     *
     * <p>Access control:
     * <ul>
     *   <li>PROPERTY_MANAGER: acknowledge, assign, verify, resolve, reject, cancel, close</li>
     *   <li>TECHNICIAN / VENDOR_TECHNICIAN: mark complete (READY_FOR_VERIFICATION)</li>
     *   <li>RESIDENT_OWNER / RESIDENT_TENANT: close, reopen, cancel (own tickets)</li>
     *   <li>COMMITTEE_MEMBER: read-only (no transition access)</li>
     * </ul>
     *
     * @param ticketId the ticket to transition
     * @param request  the transition request with target status and optional reason
     * @param authentication the authenticated user
     * @return the updated ticket with HTTP 200
     */
    @PatchMapping("/{ticketId}/status")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'TECHNICIAN', 'VENDOR_TECHNICIAN', 'RESIDENT_OWNER', 'RESIDENT_TENANT')")
    public ResponseEntity<TicketResponse> transitionStatus(
            @PathVariable UUID ticketId,
            @Valid @RequestBody TransitionTicketStatusRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Ticket ticket = ticketService.transitionStatus(ticketId, request, actingUserId);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Reopens a closed or resolved ticket within the configurable time window.
     *
     * <p>The ticket must have been closed or resolved within the configured reopen window
     * (default 72 hours). A reason for reopening is mandatory.
     *
     * <p>Access control:
     * <ul>
     *   <li>RESIDENT_OWNER / RESIDENT_TENANT: can reopen their own tickets</li>
     *   <li>PROPERTY_MANAGER: can reopen any ticket in their property</li>
     * </ul>
     *
     * @param ticketId the ticket to reopen
     * @param request  the reopen request with mandatory reason
     * @param authentication the authenticated user
     * @return the reopened ticket with HTTP 200
     */
    @PostMapping("/{ticketId}/reopen")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'RESIDENT_OWNER', 'RESIDENT_TENANT')")
    public ResponseEntity<TicketResponse> reopenTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ReopenTicketRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Ticket ticket = ticketService.reopenTicket(ticketId, request, actingUserId);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Changes the category of a ticket.
     *
     * <p>Restricted to Property Managers. Recalculates SLA targets based on the new
     * classification and records the change in status history.
     *
     * @param ticketId the ticket to update
     * @param request  the change category request with the new category
     * @param authentication the authenticated user
     * @return the updated ticket with HTTP 200
     */
    @PatchMapping("/{ticketId}/category")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<TicketResponse> changeCategory(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ChangeCategoryRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Ticket ticket = ticketService.changeCategory(ticketId, request.category(), actingUserId);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Changes the priority of a ticket.
     *
     * <p>Restricted to Property Managers. Recalculates SLA targets based on the new
     * priority, records the change in status history with previous and new values,
     * and publishes a PriorityChangedEvent for SLA recalculation and audit.
     *
     * @param ticketId the ticket to update
     * @param request  the change priority request with the new priority
     * @param authentication the authenticated user
     * @return the updated ticket with HTTP 200
     */
    @PatchMapping("/{ticketId}/priority")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<TicketResponse> changePriority(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ChangePriorityRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Ticket ticket = ticketService.changePriority(ticketId, request.priority(), actingUserId);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    /**
     * Returns the complete activity history for a ticket in chronological order.
     *
     * <p>Includes status changes, comments, assignments, and attachment uploads.
     * Every entry has a non-null author and timestamp. Internal notes are excluded
     * for resident users.
     *
     * @param ticketId the ticket to get activity history for
     * @param authentication the authenticated user
     * @return the activity history sorted by timestamp ascending
     */
    @GetMapping("/{ticketId}/activity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ActivityEntry>> getActivityHistory(
            @PathVariable UUID ticketId,
            Authentication authentication) {
        UUID requestingUserId = (UUID) authentication.getPrincipal();
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, requestingUserId);
        return ResponseEntity.ok(activities);
    }
}
