package com.smartdoc.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics as Spring beans.
 *
 * Why declare topics in code?
 * If the topic doesn't exist when the producer tries to publish,
 * Kafka auto-creates it with default settings.
 * Declaring it explicitly gives us control over partitions and
 * replication factor. Spring Kafka creates it on startup if missing.
 *
 * DLT = Dead Letter Topic.
 * If a message fails processing after all retries,
 * it goes to the DLT instead of being lost.
 * You can inspect failed messages later and reprocess them.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String DOCUMENT_PROCESSING_TOPIC = "document-processing";
    public static final String DOCUMENT_PROCESSING_DLT   = "document-processing.DLT";

    @Bean
    public NewTopic documentProcessingTopic() {
        return TopicBuilder.name(DOCUMENT_PROCESSING_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic documentProcessingDlt() {
        return TopicBuilder.name(DOCUMENT_PROCESSING_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}