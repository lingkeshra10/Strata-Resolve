package com.strataresolve.ticket.controller;

import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.dto.AssignmentResponse;
import com.strataresolve.ticket.dto.CreateAssignmentRequest;
import com.strataresolve.ticket.service.AssignmentService;
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
 * REST controller for ticket assignment operations.
 * Assignment creation is restricted to Property Managers.
 */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Creates a new ticket assignment.
     * Restricted to Property Managers.
     *
     * @param request the assignment creation request
     * @param authentication the authenticated user
     * @return the created assignment with HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<AssignmentResponse> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Assignment assignment = assignmentService.createAssignment(request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentResponse.from(assignment));
    }

    /**
     * Lists all assignments for a given ticket.
     */
    @GetMapping("/ticket/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsByTicket(@PathVariable UUID ticketId) {
        List<AssignmentResponse> assignments = assignmentService.findByTicketId(ticketId).stream()
                .map(AssignmentResponse::from)
                .toList();
        return ResponseEntity.ok(assignments);
    }

    /**
     * Lists all assignments for a given assignee.
     */
    @GetMapping("/assignee/{assigneeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsByAssignee(@PathVariable UUID assigneeId) {
        List<AssignmentResponse> assignments = assignmentService.findByAssignedTo(assigneeId).stream()
                .map(AssignmentResponse::from)
                .toList();
        return ResponseEntity.ok(assignments);
    }
}
