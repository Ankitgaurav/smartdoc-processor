package com.smartdoc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_results")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="document_id", nullable= false)
    private Document document;

    @Column(nullable=false, length =30)
    @Builder.Default
    private String status = "PENDING";

    private Boolean matched;             // true = all fields matched

    @Column(columnDefinition = "jsonb")
    private String mismatchDetails;      // JSON string of what didn't match

    @Column(columnDefinition = "jsonb")
    private String referenceData;        // snapshot of reference record used

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciled_by")
    private User reconciledBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
