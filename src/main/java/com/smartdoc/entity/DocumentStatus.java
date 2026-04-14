package com.smartdoc.entity;

/**
 * Tracks the lifecycle of a document through the processing pipeline.
 *
 * PENDING    → uploaded to S3, waiting for Kafka consumer
 * PROCESSING → Kafka consumer picked it up, AI call in progress
 * COMPLETED  → AI extraction done, data saved to DB
 * FAILED     → something went wrong, check errorMessage field
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
