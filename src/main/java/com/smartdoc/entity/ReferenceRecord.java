package com.smartdoc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a known authoritative record used for reconciliation.
 *
 * Example: A pre-approved vendor invoice that uploaded documents
 * are compared against. If the document's extracted data matches
 * a ReferenceRecord, reconciliation passes.
 *
 * Use BigDecimal for money — NEVER use double or float for currency.
 */
@Entity
@Table(name = "reference_records")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferenceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String vendorName;

    // BigDecimal is mandatory for financial data
    // NUMERIC(15,2) in SQL = precision 15, scale 2
    @Column(precision = 15, scale = 2)
    private BigDecimal expectedAmount;

    @Column(length = 10)
    private String currency;

    private LocalDate invoiceDate;

    @Column(unique = true, length = 100)
    private String referenceId;         // external reference like invoice number

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}