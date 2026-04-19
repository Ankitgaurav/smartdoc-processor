package com.smartdoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Handles all AWS S3 operations: upload and delete.
 *
 * Key decisions:
 * - S3 key format: documents/YYYY/MM/UUID.extension
 *   This organises files by date — easy to manage and audit.
 *   UUID ensures uniqueness even if same filename is uploaded twice.
 *
 * - putObject with RequestBody.fromInputStream() streams the file
 *   directly to S3 without loading entire file into memory.
 *   Critical for large files — avoids OutOfMemoryError.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.aws.region}")
    private String region;

    /**
     * Uploads a file to S3 and returns the S3 object key.
     *
     * @param file      the uploaded multipart file
     * @param extension file extension (pdf, png, jpg)
     * @return the S3 key (path) where the file is stored
     */
    public String uploadFile(MultipartFile file, String extension) {
        // Generate a unique S3 key with date-based folder structure
        String s3Key = generateS3Key(extension);

        log.info("Uploading file to S3: bucket={}, key={}, size={}bytes",
                bucketName, s3Key, file.getSize());

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("File uploaded successfully to S3: {}", s3Key);
            return s3Key;

        } catch (IOException e) {
            log.error("Failed to read file input stream: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new RuntimeException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the public-style URL for an S3 object.
     * Note: Bucket is private — this URL is for reference only.
     * Actual access requires AWS SDK with credentials.
     *
     * @param s3Key the object key returned by uploadFile()
     * @return the S3 object URL string
     */
    public String buildFileUrl(String s3Key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, region, s3Key);
    }

    /**
     * Deletes a file from S3 by its key.
     *
     * @param s3Key the S3 object key to delete
     */
    public void deleteFile(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("File deleted from S3: {}", s3Key);

        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage());
            throw new RuntimeException("S3 delete failed: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ──────────────────────────────────────

    /**
     * Generates a unique S3 key with date-based folder organisation.
     * Format: documents/2026/04/550e8400-e29b-41d4-a716.pdf
     */
    private String generateS3Key(String extension) {
        LocalDate today = LocalDate.now();
        String uuid = UUID.randomUUID().toString();
        return String.format("documents/%d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(), uuid, extension);
    }
}