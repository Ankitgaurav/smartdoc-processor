package com.smartdoc.kafka;

import com.smartdoc.config.KafkaTopicConfig;
import com.smartdoc.entity.Document;
import com.smartdoc.entity.DocumentStatus;
import com.smartdoc.repository.DocumentRepository;
import com.smartdoc.service.DocumentTextExtractor;
import com.smartdoc.service.GroqAiService;
import com.smartdoc.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.BackOff;

import java.util.Optional;

/**
 * Consumes document processing events from Kafka.
 *
 * Key design decisions:
 *
 * @RetryableTopic: if processing fails, Kafka automatically
 * retries 3 times with exponential backoff (1s, 2s, 4s).
 * After all retries fail, message goes to DLT.
 *
 * MANUAL_IMMEDIATE ack mode: we manually acknowledge the message
 * only AFTER successfully processing it. If processing fails,
 * the message is not acknowledged and gets retried.
 *
 * In Module 4, step 3 below will call OpenAI API to extract
 * structured data from the document. For now, we simulate it
 * with a placeholder that just marks it COMPLETED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEventConsumer {

    private final DocumentRepository documentRepository;
    private final RedisCacheService redisCacheService;
    private final DocumentTextExtractor documentTextExtractor;
    private final GroqAiService groqAiService;

//    @RetryableTopic(
//            attempts = "3",
//            backoff = @Backoff(delay = 1000, multiplier = 2.0),
//            dltTopicSuffix = ".DLT",
//            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
//            autoCreateTopics = "true"
//    )
    @KafkaListener(
            topics = KafkaTopicConfig.DOCUMENT_PROCESSING_TOPIC,
            groupId = "smartdoc-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeDocumentProcessingEvent(
            ConsumerRecord<String, DocumentProcessingMessage> record,
            Acknowledgment acknowledgment) {

        DocumentProcessingMessage message = record.value();
        log.info("Received: documentId={}, offset={}", message.getDocumentId(), record.offset());

        try {
            // Step 1: Fetch document from DB
            Document document = documentRepository.findById(message.getDocumentId())
                    .orElseThrow(() -> new RuntimeException(
                            "Document not found: " + message.getDocumentId()));

            // Step 2: Skip if already processed (idempotency guard)
            if (document.getStatus() == DocumentStatus.COMPLETED) {
                log.info("Document {} already COMPLETED — skipping", message.getDocumentId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Mark as PROCESSING
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);
            log.info("Document {} → PROCESSING", message.getDocumentId());

            // Step 4: Check Redis cache first
            String extractedData;
            Optional<String> cached = redisCacheService.getCachedExtraction(message.getDocumentHash());

            if (cached.isPresent()) {
                // Cache HIT — return immediately, no AI call needed
                log.info("Cache HIT — skipping Groq API call for documentId={}", message.getDocumentId());
                extractedData = cached.get();
            } else {
                // Cache MISS — download from S3, extract text, call Groq
                log.info("Cache MISS — calling Groq AI for documentId={}", message.getDocumentId());

                // Step 5: Download from S3 and extract text
                String documentText = documentTextExtractor.extractText(
                        message.getS3Key(), message.getContentType());

                // Step 6: Call Groq AI for structured extraction
                extractedData = groqAiService.extractStructuredData(
                        documentText, message.getOriginalName());

                // Step 7: Cache the result for future duplicate uploads
                redisCacheService.cacheExtraction(message.getDocumentHash(), extractedData);
            }

            // Step 8: Save result and mark COMPLETED
            document.setExtractedData(extractedData);
            document.setStatus(DocumentStatus.COMPLETED);
            documentRepository.save(document);
            log.info("Document {} → COMPLETED", message.getDocumentId());

            // Step 9: Acknowledge — tell Kafka offset is processed
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Processing failed for document {}: {}",
                    message.getDocumentId(), e.getMessage());
            // Update status to FAILED in DB
            documentRepository.findById(message.getDocumentId()).ifPresent(doc -> {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
            });
            acknowledgment.acknowledge(); // Acknowledge to avoid infinite loop
        }
    }

    /**
     * Placeholder for AI extraction — will be replaced in Module 4.
     * Returns a simple JSON string simulating extracted data.
     */
    private String processDocument(DocumentProcessingMessage message) {
        log.info("Processing document: {} ({})", message.getOriginalName(), message.getContentType());

        // TODO Module 4: call OpenAI API here to extract structured data
        // For now return placeholder JSON
        return String.format(
                "{\"status\":\"extracted\",\"filename\":\"%s\",\"note\":\"AI processing placeholder\"}",
                message.getOriginalName()
        );
    }
}