package com.strataresolve.ticket.controller;

import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.dto.AttachmentResponse;
import com.strataresolve.ticket.service.AttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for attachment operations.
 * Provides endpoints to upload, list, and download file attachments on tickets.
 *
 * <p>Access control is enforced consistent with ticket access permissions:
 * any authenticated user with access to the ticket's property can manage attachments.
 */
@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Uploads a file attachment to a ticket.
     *
     * <p>Accepts multipart/form-data with a "file" part. Validates file type
     * (JPEG, PNG, PDF) and file size (configurable, default 10MB).
     *
     * @param ticketId       the ticket to attach the file to
     * @param file           the multipart file upload
     * @param authentication the authenticated user
     * @return the attachment metadata with HTTP 201
     */
    @PostMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable UUID ticketId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        UUID uploadedBy = (UUID) authentication.getPrincipal();

        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";
        long fileSize = file.getSize();

        Attachment attachment = attachmentService.uploadAttachment(
                ticketId,
                uploadedBy,
                originalFilename,
                contentType,
                fileSize,
                file.getInputStream()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentResponse.from(attachment));
    }

    /**
     * Lists all attachments for a ticket.
     *
     * @param ticketId the ticket ID
     * @return list of attachment metadata
     */
    @GetMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttachmentResponse>> listAttachments(@PathVariable UUID ticketId) {
        List<AttachmentResponse> attachments = attachmentService.findByTicketId(ticketId).stream()
                .map(AttachmentResponse::from)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    /**
     * Downloads an attachment file by its ID.
     *
     * @param attachmentId the attachment ID
     * @return the file content with appropriate Content-Type and Content-Disposition headers
     */
    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadAttachment(@PathVariable UUID attachmentId) {
        Attachment attachment = attachmentService.findById(attachmentId);
        InputStream content = attachmentService.downloadAttachment(attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .contentLength(attachment.getFileSize())
                .body(new InputStreamResource(content));
    }
}
