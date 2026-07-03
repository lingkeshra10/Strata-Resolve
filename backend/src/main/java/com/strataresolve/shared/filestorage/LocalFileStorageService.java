package com.strataresolve.shared.filestorage;

import com.strataresolve.shared.exception.PayloadTooLargeException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.exception.UnsupportedFileTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link FileStorageService}.
 * Stores files using a UUID-based directory structure for even distribution:
 * {@code basePath / first2chars / next2chars / fullUUID.extension}
 *
 * <p>Validates file type (JPEG, PNG, PDF) and file size before storing.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private final FileStorageProperties properties;
    private final Path basePath;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
        this.basePath = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
    }

    @Override
    public FileReference store(InputStream content, FileMetadata metadata) {
        validateFileType(metadata.getContentType());
        validateFileSize(metadata.getFileSize());

        String storageReference = generateStorageReference(metadata.getOriginalFilename());
        Path targetPath = resolveStoragePath(storageReference);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored file: {} -> {}", metadata.getOriginalFilename(), storageReference);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file: " + metadata.getOriginalFilename(), e);
        }

        return new FileReference(storageReference, metadata);
    }

    @Override
    public InputStream retrieve(FileReference reference) {
        Path filePath = resolveStoragePath(reference.getStorageReference());

        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("File not found: " + reference.getStorageReference());
        }

        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to retrieve file: " + reference.getStorageReference(), e);
        }
    }

    @Override
    public void delete(FileReference reference) {
        Path filePath = resolveStoragePath(reference.getStorageReference());

        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Deleted file: {}", reference.getStorageReference());
            } else {
                log.warn("File not found for deletion: {}", reference.getStorageReference());
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file: " + reference.getStorageReference(), e);
        }
    }

    /**
     * Validates that the content type is in the allowed set (JPEG, PNG, PDF).
     */
    private void validateFileType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new UnsupportedFileTypeException(
                    "Unsupported file type: " + contentType + ". Allowed types: JPEG, PNG, PDF");
        }
    }

    /**
     * Validates that the file size does not exceed the configured maximum.
     */
    private void validateFileSize(long fileSize) {
        if (fileSize > properties.getMaxFileSizeBytes()) {
            throw new PayloadTooLargeException(
                    "File size " + fileSize + " bytes exceeds the maximum allowed size of "
                            + properties.getMaxFileSizeBytes() + " bytes");
        }
    }

    /**
     * Generates a unique storage reference using UUID-based directory structure.
     * Format: first2chars/next2chars/fullUUID.extension
     * This provides even file distribution across directories.
     */
    private String generateStorageReference(String originalFilename) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = extractExtension(originalFilename);

        String dir1 = uuid.substring(0, 2);
        String dir2 = uuid.substring(2, 4);
        String filename = uuid + extension;

        return dir1 + "/" + dir2 + "/" + filename;
    }

    /**
     * Extracts the file extension from the original filename (including the dot).
     * Returns an empty string if no extension is found.
     */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0) {
            return filename.substring(dotIndex);
        }
        return "";
    }

    /**
     * Resolves the full filesystem path for a given storage reference.
     */
    private Path resolveStoragePath(String storageReference) {
        return basePath.resolve(storageReference).normalize();
    }
}
