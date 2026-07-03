package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.filestorage.FileMetadata;
import com.strataresolve.shared.filestorage.FileReference;
import com.strataresolve.shared.filestorage.FileStorageService;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for attachment operations.
 * Handles file upload validation, storage, and metadata persistence.
 *
 * <p>File type validation (JPEG, PNG, PDF) and size validation are delegated
 * to the {@link FileStorageService} which throws appropriate exceptions
 * for unsupported types (415) and oversized files (413).
 */
@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final FileStorageService fileStorageService;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             TicketRepository ticketRepository,
                             FileStorageService fileStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Uploads a file attachment to a ticket.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the ticket exists</li>
     *   <li>Stores the file via FileStorageService (which validates type and size)</li>
     *   <li>Creates and persists an Attachment entity with metadata</li>
     * </ol>
     *
     * @param ticketId        the ticket to attach the file to
     * @param uploadedBy      the UUID of the uploading user
     * @param originalFilename the original filename
     * @param contentType     the MIME content type of the file
     * @param fileSize        the size of the file in bytes
     * @param content         the file content as an input stream
     * @return the persisted Attachment entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws com.strataresolve.shared.exception.UnsupportedFileTypeException if the file type is not allowed
     * @throws com.strataresolve.shared.exception.PayloadTooLargeException if the file exceeds the size limit
     */
    public Attachment uploadAttachment(UUID ticketId, UUID uploadedBy,
                                       String originalFilename, String contentType,
                                       long fileSize, InputStream content) {
        // 1. Validate ticket exists
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // 2. Build metadata and store file (validation happens inside FileStorageService)
        Instant uploadedAt = Instant.now();
        FileMetadata metadata = new FileMetadata(originalFilename, contentType, fileSize, uploadedAt, uploadedBy);
        FileReference fileReference = fileStorageService.store(content, metadata);

        // 3. Create and persist attachment entity
        Attachment attachment = Attachment.builder()
                .ticketId(ticketId)
                .uploadedBy(uploadedBy)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileSize(fileSize)
                .storageReference(fileReference.getStorageReference())
                .uploadedAt(uploadedAt)
                .build();

        Attachment saved = attachmentRepository.save(attachment);

        log.info("Attachment uploaded: {} (file: {}) to ticket {} by user {}",
                saved.getId(), originalFilename, ticketId, uploadedBy);

        return saved;
    }

    /**
     * Lists all attachments for a given ticket.
     *
     * @param ticketId the ticket ID
     * @return list of attachments ordered by upload time
     * @throws ResourceNotFoundException if the ticket is not found
     */
    @Transactional(readOnly = true)
    public List<Attachment> findByTicketId(UUID ticketId) {
        // Validate ticket exists
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        return attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId);
    }

    /**
     * Retrieves an attachment by its ID.
     *
     * @param attachmentId the attachment ID
     * @return the attachment entity
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Attachment findById(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
    }

    /**
     * Downloads the content of an attachment.
     *
     * @param attachmentId the attachment ID
     * @return the file content as an input stream
     * @throws ResourceNotFoundException if the attachment or file is not found
     */
    @Transactional(readOnly = true)
    public InputStream downloadAttachment(UUID attachmentId) {
        Attachment attachment = findById(attachmentId);

        FileMetadata metadata = new FileMetadata(
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploadedBy()
        );
        FileReference fileReference = new FileReference(attachment.getStorageReference(), metadata);

        return fileStorageService.retrieve(fileReference);
    }
}
