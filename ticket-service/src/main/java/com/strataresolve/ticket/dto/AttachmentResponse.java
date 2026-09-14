package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Attachment;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing an attachment.
 */
public record AttachmentResponse(
        UUID id,
        UUID ticketId,
        UUID uploadedBy,
        String originalFilename,
        String contentType,
        long fileSize,
        Instant uploadedAt
) {
    /**
     * Creates an AttachmentResponse from an Attachment entity.
     */
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicketId(),
                attachment.getUploadedBy(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt()
        );
    }
}
