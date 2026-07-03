package com.strataresolve.shared.exception;

/**
 * Thrown when a file upload has a type not in the allowed set.
 */
public class UnsupportedFileTypeException extends BaseBusinessException {

    public UnsupportedFileTypeException(String message) {
        super(message, "UNSUPPORTED_FILE_TYPE");
    }
}
