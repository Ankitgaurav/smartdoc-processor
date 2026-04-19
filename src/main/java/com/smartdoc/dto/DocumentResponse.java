package com.smartdoc.dto;

import com.smartdoc.entity.DocumentStatus;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Response DTO for document metadata.
 * Never exposes the full entity — only what the client needs.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentResponse {
    private Long id;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private DocumentStatus status;
    private String s3Url;
    private String documentHash;
    private String uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}