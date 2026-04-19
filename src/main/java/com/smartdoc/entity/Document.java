package com.smartdoc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name="documents")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;     // system-generated UUID filename

    @Column(nullable = false)
    private String originalName;    // what the user named the file

    @Column(nullable = false, unique = true, length =500)
    private String s3Key;          // path inside S3 bucket

    @Column(length = 1000)
    private String s3Url;         // full S3 URL

    @Column(length = 100)
    private String contentType;        // e.g. application/pdf

    private Long fileSize;             // in bytes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length=30)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(length = 64)
    private String documentHash;       // SHA-256 of file content

    // Stored as JSONB in PostgreSQL.
    // Example: {"vendorName":"Acme","totalAmount":1500.00,"currency":"INR"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private String extractedData; // store as JSON String, easier to manage

    @Column(columnDefinition = "TEXT")
    private String errorMessage;       // populated only when status = FAILED

    // Many documents → One user
    // LAZY loading = don't fetch User from DB unless explicitly accessed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;


}