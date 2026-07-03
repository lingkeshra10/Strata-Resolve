package com.strataresolve.shared.filestorage;

/**
 * Runtime exception wrapping I/O errors during file storage operations.
 * This is an infrastructure-level exception that indicates an unexpected failure
 * in the underlying storage mechanism.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileStorageException(String message) {
        super(message);
    }
}
