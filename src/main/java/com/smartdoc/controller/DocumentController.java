package com.smartdoc.controller;

import com.smartdoc.dto.ApiResponse;
import com.smartdoc.dto.DocumentResponse;
import com.smartdoc.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Upload, manage, and retrieve AI-processed documents")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a document",
            description = "Upload PDF, PNG, or JPG. File is stored in S3, metadata saved to PostgreSQL, and Kafka event triggers AI extraction.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file type or size"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — JWT required")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @Parameter(description = "PDF, PNG or JPG file to upload", required = true)
            @RequestParam("file") MultipartFile file) {

        DocumentResponse response = documentService.uploadDocument(file);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, response, "Document uploaded successfully",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "Get document by ID",
            description = "Returns metadata for a specific document. Users can only access their own documents.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found or access denied")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long id) {

        DocumentResponse response = documentService.getDocumentById(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, response, "Document retrieved successfully",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "List my documents",
            description = "Returns all non-deleted documents for the authenticated user. No pagination.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments() {
        List<DocumentResponse> documents = documentService.getMyDocuments();
        return ResponseEntity.ok(
                new ApiResponse<>(true, documents, "Documents retrieved successfully",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "List documents with pagination and filters",
            description = "Paginated list. Filter by status (PENDING, PROCESSING, COMPLETED, FAILED) and date range (yyyy-MM-dd).")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DocumentResponse>>> getDocumentsPaginated(
            @Parameter(description = "Filter by status: PENDING, PROCESSING, COMPLETED, FAILED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter from date (yyyy-MM-dd)")
            @RequestParam(required = false) String from,
            @Parameter(description = "Filter to date (yyyy-MM-dd)")
            @RequestParam(required = false) String to,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        Page<DocumentResponse> documents =
                documentService.getMyDocumentsPaginated(status, from, to, page, size);
        return ResponseEntity.ok(
                new ApiResponse<>(true, documents, "Documents retrieved successfully",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "Get AI extraction result",
            description = "Returns the structured JSON data extracted by Groq AI. Only available when status is COMPLETED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Extraction result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Document not yet completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/extraction")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtraction(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long id) {

        Map<String, Object> result = documentService.getExtractionResult(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, result, "Extraction result retrieved",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "Soft delete a document",
            description = "Marks document as deleted in DB, evicts Redis cache, and deletes file from S3. Data is NOT permanently removed.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found or access denied")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteDocument(
            @Parameter(description = "Document ID to delete", required = true)
            @PathVariable Long id) {

        documentService.deleteDocument(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Document deleted", "Document deleted successfully",
                        LocalDateTime.now().toString()));
    }

    @Operation(summary = "Force reprocess a document",
            description = "Resets status to PENDING, clears extracted data, evicts Redis cache, and republishes Kafka event for fresh AI extraction.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reprocessing started"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ApiResponse<String>> reprocessDocument(
            @Parameter(description = "Document ID to reprocess", required = true)
            @PathVariable Long id) {

        ApiResponse<String> response = documentService.reprocessDocument(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all documents (ADMIN only)",
            description = "Returns all documents in the system. Requires ADMIN role.")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAllDocuments() {
        List<DocumentResponse> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(
                new ApiResponse<>(true, documents, "All documents retrieved",
                        LocalDateTime.now().toString()));
    }
}