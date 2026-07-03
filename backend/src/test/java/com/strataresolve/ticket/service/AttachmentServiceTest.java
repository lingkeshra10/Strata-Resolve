package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.PayloadTooLargeException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.exception.UnsupportedFileTypeException;
import com.strataresolve.shared.filestorage.FileMetadata;
import com.strataresolve.shared.filestorage.FileReference;
import com.strataresolve.shared.filestorage.FileStorageService;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttachmentService covering:
 * - Successful upload of valid JPEG, PNG, PDF files
 * - Rejection of unsupported file types
 * - Rejection of oversized files
 * - Metadata stored correctly
 * - Ticket existence validation
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AttachmentService attachmentService;

    private UUID ticketId;
    private UUID uploadedBy;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        uploadedBy = UUID.randomUUID();
        ticket = Ticket.builder()
                .id(ticketId)
                .build();
        ticket.setPropertyId(UUID.randomUUID());
    }

    @Test
    void uploadAttachment_withValidJpeg_storesSuccessfully() {
        // Arrange
        byte[] content = "fake jpeg data".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String filename = "photo.jpg";
        String contentType = "image/jpeg";
        long fileSize = content.length;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        FileReference fileReference = new FileReference(
                "ab/cd/abcdef1234567890.jpg",
                new FileMetadata(filename, contentType, fileSize, Instant.now(), uploadedBy)
        );
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class))).thenReturn(fileReference);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        // Act
        Attachment result = attachmentService.uploadAttachment(
                ticketId, uploadedBy, filename, contentType, fileSize, inputStream);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(ticketId);
        assertThat(result.getUploadedBy()).isEqualTo(uploadedBy);
        assertThat(result.getOriginalFilename()).isEqualTo(filename);
        assertThat(result.getContentType()).isEqualTo(contentType);
        assertThat(result.getFileSize()).isEqualTo(fileSize);
        assertThat(result.getStorageReference()).isEqualTo("ab/cd/abcdef1234567890.jpg");

        verify(fileStorageService).store(any(InputStream.class), any(FileMetadata.class));
        verify(attachmentRepository).save(any(Attachment.class));
    }

    @Test
    void uploadAttachment_withValidPng_storesSuccessfully() {
        // Arrange
        byte[] content = "fake png data".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String filename = "screenshot.png";
        String contentType = "image/png";
        long fileSize = content.length;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        FileReference fileReference = new FileReference(
                "ef/gh/efgh1234567890.png",
                new FileMetadata(filename, contentType, fileSize, Instant.now(), uploadedBy)
        );
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class))).thenReturn(fileReference);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        // Act
        Attachment result = attachmentService.uploadAttachment(
                ticketId, uploadedBy, filename, contentType, fileSize, inputStream);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getOriginalFilename()).isEqualTo("screenshot.png");
    }

    @Test
    void uploadAttachment_withValidPdf_storesSuccessfully() {
        // Arrange
        byte[] content = "fake pdf data".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String filename = "report.pdf";
        String contentType = "application/pdf";
        long fileSize = content.length;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        FileReference fileReference = new FileReference(
                "ij/kl/ijkl1234567890.pdf",
                new FileMetadata(filename, contentType, fileSize, Instant.now(), uploadedBy)
        );
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class))).thenReturn(fileReference);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        // Act
        Attachment result = attachmentService.uploadAttachment(
                ticketId, uploadedBy, filename, contentType, fileSize, inputStream);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getOriginalFilename()).isEqualTo("report.pdf");
    }

    @Test
    void uploadAttachment_withUnsupportedFileType_throwsException() {
        // Arrange
        byte[] content = "text content".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String filename = "readme.txt";
        String contentType = "text/plain";
        long fileSize = content.length;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class)))
                .thenThrow(new UnsupportedFileTypeException(
                        "Unsupported file type: text/plain. Allowed types: JPEG, PNG, PDF"));

        // Act & Assert
        assertThatThrownBy(() -> attachmentService.uploadAttachment(
                ticketId, uploadedBy, filename, contentType, fileSize, inputStream))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("text/plain");
    }

    @Test
    void uploadAttachment_withOversizedFile_throwsException() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        String filename = "large-file.pdf";
        String contentType = "application/pdf";
        long fileSize = 20 * 1024 * 1024; // 20 MB

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class)))
                .thenThrow(new PayloadTooLargeException(
                        "File size " + fileSize + " bytes exceeds the maximum allowed size"));

        // Act & Assert
        assertThatThrownBy(() -> attachmentService.uploadAttachment(
                ticketId, uploadedBy, filename, contentType, fileSize, inputStream))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void uploadAttachment_withNonExistentTicket_throwsResourceNotFoundException() {
        // Arrange
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        UUID nonExistentTicketId = UUID.randomUUID();

        when(ticketRepository.findById(nonExistentTicketId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> attachmentService.uploadAttachment(
                nonExistentTicketId, uploadedBy, "file.jpg", "image/jpeg", content.length, inputStream))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadAttachment_storesCorrectMetadata() {
        // Arrange
        byte[] content = "metadata test".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String filename = "evidence.jpg";
        String contentType = "image/jpeg";
        long fileSize = content.length;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        FileReference fileReference = new FileReference(
                "mn/op/mnop1234567890.jpg",
                new FileMetadata(filename, contentType, fileSize, Instant.now(), uploadedBy)
        );
        when(fileStorageService.store(any(InputStream.class), any(FileMetadata.class))).thenReturn(fileReference);

        ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
        when(attachmentRepository.save(attachmentCaptor.capture())).thenAnswer(invocation -> {
            Attachment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        // Act
        attachmentService.uploadAttachment(ticketId, uploadedBy, filename, contentType, fileSize, inputStream);

        // Assert - verify metadata stored correctly
        Attachment saved = attachmentCaptor.getValue();
        assertThat(saved.getTicketId()).isEqualTo(ticketId);
        assertThat(saved.getUploadedBy()).isEqualTo(uploadedBy);
        assertThat(saved.getOriginalFilename()).isEqualTo("evidence.jpg");
        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getFileSize()).isEqualTo(fileSize);
        assertThat(saved.getStorageReference()).isEqualTo("mn/op/mnop1234567890.jpg");
        assertThat(saved.getUploadedAt()).isNotNull();
    }

    @Test
    void findByTicketId_returnsAttachments() {
        // Arrange
        Attachment attachment1 = Attachment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .uploadedBy(uploadedBy)
                .originalFilename("photo1.jpg")
                .contentType("image/jpeg")
                .fileSize(1000)
                .storageReference("aa/bb/photo1.jpg")
                .uploadedAt(Instant.now())
                .build();
        Attachment attachment2 = Attachment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .uploadedBy(uploadedBy)
                .originalFilename("report.pdf")
                .contentType("application/pdf")
                .fileSize(2000)
                .storageReference("cc/dd/report.pdf")
                .uploadedAt(Instant.now())
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenReturn(List.of(attachment1, attachment2));

        // Act
        List<Attachment> result = attachmentService.findByTicketId(ticketId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOriginalFilename()).isEqualTo("photo1.jpg");
        assertThat(result.get(1).getOriginalFilename()).isEqualTo("report.pdf");
    }

    @Test
    void findByTicketId_withNonExistentTicket_throwsResourceNotFoundException() {
        UUID nonExistentTicketId = UUID.randomUUID();
        when(ticketRepository.findById(nonExistentTicketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.findByTicketId(nonExistentTicketId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadAttachment_returnsFileContent() {
        // Arrange
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .ticketId(ticketId)
                .uploadedBy(uploadedBy)
                .originalFilename("photo.jpg")
                .contentType("image/jpeg")
                .fileSize(100)
                .storageReference("ab/cd/photo.jpg")
                .uploadedAt(Instant.now())
                .build();

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        InputStream expectedContent = new ByteArrayInputStream("file content".getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.retrieve(any(FileReference.class))).thenReturn(expectedContent);

        // Act
        InputStream result = attachmentService.downloadAttachment(attachmentId);

        // Assert
        assertThat(result).isEqualTo(expectedContent);
    }

    @Test
    void downloadAttachment_withNonExistentAttachment_throwsResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(attachmentRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.downloadAttachment(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
