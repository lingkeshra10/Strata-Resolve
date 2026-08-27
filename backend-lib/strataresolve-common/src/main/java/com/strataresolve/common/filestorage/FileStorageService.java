package com.strataresolve.common.filestorage;

import java.io.InputStream;

/**
 * Abstraction for file storage operations.
 * Decouples business logic from the underlying storage mechanism,
 * allowing future migration between storage providers (local filesystem, S3, etc.).
 */
public interface FileStorageService {

    /**
     * Stores a file with the given content and metadata.
     * Validates file type and size before persisting.
     *
     * @param content  the file content as an input stream
     * @param metadata metadata about the file (filename, content type, size, uploader)
     * @return a reference to the stored file
     * @throws com.strataresolve.shared.exception.UnsupportedFileTypeException if the file type is not allowed
     * @throws com.strataresolve.shared.exception.PayloadTooLargeException     if the file exceeds the size limit
     */
    FileReference store(InputStream content, FileMetadata metadata);

    /**
     * Retrieves the content of a previously stored file.
     *
     * @param reference the file reference obtained from a prior store operation
     * @return the file content as an input stream
     * @throws com.strataresolve.shared.exception.ResourceNotFoundException if the file cannot be found
     */
    InputStream retrieve(FileReference reference);

    /**
     * Deletes a previously stored file.
     *
     * @param reference the file reference obtained from a prior store operation
     */
    void delete(FileReference reference);
}
