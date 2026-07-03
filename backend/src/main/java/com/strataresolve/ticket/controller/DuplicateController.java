package com.strataresolve.ticket.controller;

import com.strataresolve.shared.tenant.TenantContext;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.dto.DuplicateLinkResponse;
import com.strataresolve.ticket.dto.LinkDuplicateRequest;
import com.strataresolve.ticket.dto.TicketResponse;
import com.strataresolve.ticket.service.DuplicateDetectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for duplicate ticket detection and management.
 * Provides endpoints for Property Managers to review flagged duplicates
 * and manually link duplicate tickets to a primary ticket.
 */
@RestController
@RequestMapping("/api/tickets/duplicates")
public class DuplicateController {

    private final DuplicateDetectionService duplicateDetectionService;

    public DuplicateController(DuplicateDetectionService duplicateDetectionService) {
        this.duplicateDetectionService = duplicateDetectionService;
    }

    /**
     * Lists all tickets flagged as potential duplicates in the current property.
     * Restricted to Property Managers for review.
     *
     * @return list of flagged tickets
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<TicketResponse>> getFlaggedDuplicates() {
        UUID propertyId = TenantContext.getCurrentPropertyId();
        List<TicketResponse> flagged = duplicateDetectionService.getFlaggedDuplicates(propertyId).stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(flagged);
    }

    /**
     * Manually links a duplicate ticket to a primary ticket.
     * Only Property Managers can perform this action.
     *
     * @param request        the link request containing primary and duplicate ticket IDs
     * @param authentication the authenticated user
     * @return the created duplicate link with HTTP 201
     */
    @PostMapping("/link")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<DuplicateLinkResponse> linkDuplicate(
            @Valid @RequestBody LinkDuplicateRequest request,
            Authentication authentication) {
        UUID linkedBy = (UUID) authentication.getPrincipal();
        DuplicateLinkResponse response = duplicateDetectionService.linkDuplicate(
                request.primaryTicketId(),
                request.duplicateTicketId(),
                linkedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets all duplicate links associated with a specific ticket.
     * Returns links where the ticket is either the primary or the duplicate.
     *
     * @param ticketId the ticket ID
     * @return list of duplicate links
     */
    @GetMapping("/{ticketId}/links")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'COMMITTEE_MEMBER')")
    public ResponseEntity<List<DuplicateLinkResponse>> getDuplicateLinks(@PathVariable UUID ticketId) {
        List<DuplicateLinkResponse> links = duplicateDetectionService.getDuplicateLinks(ticketId);
        return ResponseEntity.ok(links);
    }
}
