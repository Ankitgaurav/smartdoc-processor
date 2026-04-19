package com.smartdoc.service;

import com.smartdoc.dto.DocumentResponse;
import com.smartdoc.entity.Document;
import com.smartdoc.entity.DocumentStatus;
import com.smartdoc.entity.User;
import com.smartdoc.exception.BadRequestException;
import com.smartdoc.exception.ResourceNotFoundException;
import com.smartdoc.repository.DocumentRepository;
import com.smartdoc.repository.UserRepository;
import com.smartdoc.security.UserDetailsImpl;
import com.smartdoc.util.FileHashUtil;
import com.smartdoc.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Core service handling document upload and retrieval.
 *
 * Upload flow:
 * 1. Validate file (type, size)
 * 2. Compute SHA-256 hash for deduplication
 * 3. Upload file to AWS S3
 * 4. Save document metadata to PostgreSQL (status: PENDING)
 * 5. Return DocumentResponse to client
 *
 * In Module 3, after step 4 we will also publish a Kafka message
 * to trigger async AI processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    /**
     * Handles the full document upload flow.
     *
     * @param file the uploaded multipart file
     * @return DocumentResponse with metadata and current status
     */
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {

        // Step 1: Validate file type and size
        FileValidator.validate(file);

        // Step 2: Get the currently logged-in user from SecurityContext
        User currentUser = getCurrentUser();

        // Step 3: Compute file hash for deduplication (used in Redis later)
        String fileHash;
        try {
            fileHash = FileHashUtil.computeSha256(file.getInputStream());
            log.debug("Computed file hash: {}", fileHash);
        } catch (Exception e) {
            throw new BadRequestException("Could not read file content");
        }

        // Step 4: Get file extension and upload to S3
        String extension = FileValidator.getExtension(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file"
        );
        String s3Key = s3Service.uploadFile(file, extension);
        String s3Url = s3Service.buildFileUrl(s3Key);

        // Step 5: Save document metadata to PostgreSQL
        Document document = Document.builder()
                .filename(s3Key.substring(s3Key.lastIndexOf('/') + 1))
                .originalName(file.getOriginalFilename())
                .s3Key(s3Key)
                .s3Url(s3Url)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(DocumentStatus.PENDING)
                .documentHash(fileHash)
                .uploadedBy(currentUser)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document saved to DB: id={}, user={}, status={}",
                saved.getId(), currentUser.getUsername(), saved.getStatus());

        // Step 6: Return response DTO
        return mapToResponse(saved);
    }

    /**
     * Retrieves a document by ID.
     * ADMIN can access any document.
     * USER can only access their own documents.
     *
     * @param documentId the document ID
     * @return DocumentResponse with current metadata
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document", "id", documentId));

        // Role-based access check
        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        boolean isOwner = Objects.equals(
                document.getUploadedBy().getId(),
                currentUser.getId()
        );

        if (!isAdmin && !isOwner) {
            throw new ResourceNotFoundException("Document", "id", documentId);
            // Intentionally throw 404 not 403 — don't reveal document existence to other users
        }

        return mapToResponse(document);
    }

    /**
     * Returns all documents uploaded by the currently logged-in user.
     *
     * @return list of DocumentResponse for the current user
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments() {
        User currentUser = getCurrentUser();
        return documentRepository.findByUploadedBy(currentUser)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns all documents in the system (ADMIN only).
     * Controller enforces ADMIN role via @PreAuthorize.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ──────────────────────────────────────

    /**
     * Gets the currently authenticated user from the SecurityContext.
     * Always available after JwtAuthenticationFilter runs.
     */
    private User getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();

        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "username", userDetails.getUsername()));
    }

    /**
     * Maps a Document entity to a DocumentResponse DTO.
     * Never exposes the entity directly to the API layer.
     */
    private DocumentResponse mapToResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .originalName(doc.getOriginalName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .s3Url(doc.getS3Url())
                .documentHash(doc.getDocumentHash())
                .uploadedBy(doc.getUploadedBy().getUsername())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}