package com.velorix.sentinel.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Pre-storage gate for capture uploads: extension, MIME type, size and emptiness.
 */
public interface UploadValidationService {

    /**
     * Validates the multipart part and returns its sanitised original filename.
     *
     * @throws com.velorix.sentinel.exception.BadRequestException      for a rejected file
     * @throws com.velorix.sentinel.exception.PayloadTooLargeException when the file is too large
     */
    String validate(MultipartFile file);

    /** Maximum accepted capture size in bytes. */
    long maxFileSizeBytes();
}
