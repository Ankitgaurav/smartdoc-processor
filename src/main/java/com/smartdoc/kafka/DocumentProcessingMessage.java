package com.smartdoc.kafka;

import lombok.*;

/**
 * Message object published to Kafka topic "document-processing".
 *
 * This is serialized to JSON by the Kafka producer
 * and deserialized back by the consumer.
 *
 * Keep it simple — only include what the consumer needs
 * to process the document. Never put large payloads in Kafka.
 * Kafka is for event notification, not data transfer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DocumentProcessingMessage {

    private Long documentId;       // DB id of the document to process
    private String s3Key;          // location in S3 to download the file from
    private String originalName;   // original filename (for logging/context)
    private String contentType;    // pdf, image — AI processing differs per type
    private Long uploadedByUserId; // which user uploaded — for audit trail
    private String documentHash;   // SHA-256 hash — for Redis cache lookup later
}