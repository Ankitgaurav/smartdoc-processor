package com.smartdoc.util;

import com.smartdoc.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Utility class for validating uploaded files.
 *
 * Rules enforced:
 * - Only PDF, PNG, JPG, JPEG allowed
 * - Max size: 10MB
 * - File must not be empty
 *
 * Why validate content type AND extension?
 * A malicious user can rename a .exe file to .pdf.
 * Checking content type (MIME type detected by the browser/OS)
 * adds a second layer of validation.
 */
public class FileValidator {

    // Allowed MIME types
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg"
    );

    // Allowed file extensions
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            "pdf", "png", "jpg", "jpeg"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB in bytes

    // Private constructor — this is a utility class, not meant to be instantiated
    private FileValidator() {}

    /**
     * Validates the uploaded file for type and size.
     * Throws BadRequestException with a clear message if validation fails.
     *
     * @param file the uploaded multipart file
     */
    public static void validate(MultipartFile file) {

        // Check 1: File must not be empty
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File cannot be empty");
        }

        // Check 2: File size must not exceed 10MB
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(
                    "File size " + (file.getSize() / (1024 * 1024)) + "MB exceeds maximum allowed size of 10MB"
            );
        }

        // Check 3: Content type must be allowed
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "File type '" + contentType + "' is not allowed. " +
                            "Only PDF, PNG, JPG files are accepted."
            );
        }

        // Check 4: File extension must be allowed
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BadRequestException("File name cannot be empty");
        }

        String extension = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException(
                    "File extension '." + extension + "' is not allowed. " +
                            "Only .pdf, .png, .jpg, .jpeg are accepted."
            );
        }
    }

    /**
     * Extracts the file extension from a filename.
     * Example: "invoice.pdf" → "pdf"
     */
    public static String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}