package com.smartdoc.kafka;

import com.smartdoc.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes document processing events to the Kafka topic.
 *
 * KafkaTemplate is Spring's abstraction over the Kafka producer.
 * It handles serialization, partitioning, and connection management.
 *
 * send() is async — it returns CompletableFuture immediately.
 * We attach callbacks to log success or failure.
 *
 * Interview point: What happens if Kafka is down when we publish?
 * KafkaTemplate buffers messages internally and retries.
 * If broker is unreachable after retries, the future completes exceptionally.
 * In production, you would use the Outbox Pattern to guarantee delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEventProducer {

    private final KafkaTemplate<String, DocumentProcessingMessage> kafkaTemplate;

    /**
     * Publishes a document processing event to Kafka.
     * Uses documentId as the message key — ensures all events
     * for the same document go to the same partition (ordering guarantee).
     *
     * @param message the processing event to publish
     */
    public void publishDocumentProcessingEvent(DocumentProcessingMessage message) {
        String key = String.valueOf(message.getDocumentId());

        log.info("Publishing document processing event: documentId={}, topic={}",
                message.getDocumentId(), KafkaTopicConfig.DOCUMENT_PROCESSING_TOPIC);

        CompletableFuture<SendResult<String, DocumentProcessingMessage>> future =
                kafkaTemplate.send(KafkaTopicConfig.DOCUMENT_PROCESSING_TOPIC, key, message);

        // Attach callbacks for logging
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Event published successfully: documentId={}, partition={}, offset={}",
                        message.getDocumentId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event: documentId={}, error={}",
                        message.getDocumentId(), ex.getMessage());
            }
        });
    }
}