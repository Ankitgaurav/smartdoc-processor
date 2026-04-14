package com.smartdoc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


/**
 * Activates Spring Data JPA auditing.
 *
 * Without this, @CreatedDate and @LastModifiedDate
 * on entities will never be auto-populated.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    // No code needed — @EnableJpaAuditing does all the work
}
