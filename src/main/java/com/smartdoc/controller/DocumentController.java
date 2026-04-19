package com.smartdoc.controller;

import com.smartdoc.dto.ApiResponse;
import com.smartdoc.dto.DocumentResponse;
import com.smartdoc.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for document upload and retrieval.
 *
 * Design: thin controller — all logic is in DocumentService.
 * Controller only handles HTTP concerns: status codes, response wrapping.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload and retrieval")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Uploads a document file to the system.
     * Accepts: PDF, PNG, JPG — max 10MB.
     * File is stored on AWS S3, metadata saved to PostgreSQL.
     */
    @PostMapping(value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document",
            description = "Upload PDF or image file. Max size: 10MB")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        DocumentResponse response = documentService.uploadDocument(file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response,
                        "Document uploaded successfully. Processing will begin shortly."));
    }

    /**
     * Returns metadata for a specific document by ID.
     * USER sees only their own documents.
     * ADMIN sees any document.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocumentById(
            @PathVariable Long id) {

        DocumentResponse response = documentService.getDocumentById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Document retrieved successfully"));
    }

    /**
     * Returns all documents uploaded by the currently logged-in user.
     */
    @GetMapping("/my")
    @Operation(summary = "Get my documents",
            description = "Returns all documents uploaded by the logged-in user")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments() {

        List<DocumentResponse> documents = documentService.getMyDocuments();
        return ResponseEntity.ok(
                ApiResponse.success(documents,
                        "Found " + documents.size() + " document(s)"));
    }

    /**
     * Returns ALL documents in the system — ADMIN only.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all documents (ADMIN only)")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAllDocuments() {

        List<DocumentResponse> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(
                ApiResponse.success(documents,
                        "Total documents: " + documents.size()));
    }
}