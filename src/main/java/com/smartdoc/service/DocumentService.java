package com.smartdoc.service;

import com.smartdoc.dto.ApiResponse;
import com.smartdoc.dto.DocumentResponse;
import com.smartdoc.entity.Document;
import com.smartdoc.entity.DocumentStatus;
import com.smartdoc.entity.User;
import com.smartdoc.exception.BadRequestException;
import com.smartdoc.exception.ResourceNotFoundException;
import com.smartdoc.kafka.DocumentEventProducer;
import com.smartdoc.kafka.DocumentProcessingMessage;
import com.smartdoc.repository.DocumentRepository;
import com.smartdoc.repository.UserRepository;
import com.smartdoc.security.UserDetailsImpl;
import com.smartdoc.util.FileHashUtil;
import com.smartdoc.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final DocumentEventProducer documentEventProducer;
    private final RedisCacheService redisCacheService;

    // ─── Upload ───────────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {

        FileValidator.validate(file);
        User currentUser = getCurrentUser();

        String fileHash;
        try {
            fileHash = FileHashUtil.computeSha256(file.getInputStream());
        } catch (Exception e) {
            throw new BadRequestException("Could not read file content");
        }

        String extension = FileValidator.getExtension(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String s3Key = s3Service.uploadFile(file, extension);
        String s3Url = s3Service.buildFileUrl(s3Key);

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

        DocumentProcessingMessage kafkaMessage = DocumentProcessingMessage.builder()
                .documentId(saved.getId())
                .s3Key(saved.getS3Key())
                .originalName(saved.getOriginalName())
                .contentType(saved.getContentType())
                .uploadedByUserId(currentUser.getId())
                .documentHash(saved.getDocumentHash())
                .build();

        documentEventProducer.publishDocumentProcessingEvent(kafkaMessage);
        log.info("Kafka event published for documentId={}", saved.getId());

        return mapToResponse(saved);
    }

    // ─── Get Single Document ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (document.isDeleted()) {
            throw new ResourceNotFoundException("Document", "id", documentId);
        }

        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        boolean isOwner = Objects.equals(
                document.getUploadedBy().getId(), currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new ResourceNotFoundException("Document", "id", documentId);
        }

        return mapToResponse(document);
    }

    // ─── List My Documents (basic) ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments() {
        User currentUser = getCurrentUser();
        return documentRepository.findByUploadedBy(currentUser.getUsername())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── List with Pagination + Filters ──────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getMyDocumentsPaginated(
            String status, String from, String to,
            int page, int size) {

        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);

        String statusParam = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        String fromParam   = (from != null && !from.isBlank()) ? from : null;
        String toParam     = (to != null && !to.isBlank()) ? to : null;

        return documentRepository
                .findByFilters(currentUser.getId(), statusParam, fromParam, toParam, pageable)
                .map(this::mapToResponse);
    }

    // ─── Get Extraction Result ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getExtractionResult(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = findDocumentForUser(documentId, currentUser);

        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new BadRequestException(
                    "Extraction not yet completed. Current status: " + document.getStatus());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", document.getId());
        response.put("originalName", document.getOriginalName());
        response.put("status", document.getStatus());
        response.put("extractedData", document.getExtractedData());
        response.put("updatedAt", document.getUpdatedAt());
        return response;
    }

    // ─── Soft Delete ──────────────────────────────────────────────────────────

    @Transactional
    public void deleteDocument(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = findDocumentForUser(documentId, currentUser);

        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        redisCacheService.evictCache(document.getDocumentHash());
        s3Service.deleteFile(document.getS3Key());

        log.info("Document {} soft deleted by {}", documentId, currentUser.getUsername());
    }

    // ─── Force Reprocess ─────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<String> reprocessDocument(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = findDocumentForUser(documentId, currentUser);

        document.setStatus(DocumentStatus.PENDING);
        document.setExtractedData(null);
        document.setErrorMessage(null);
        documentRepository.save(document);

        redisCacheService.evictCache(document.getDocumentHash());

        DocumentProcessingMessage kafkaMessage = DocumentProcessingMessage.builder()
                .documentId(document.getId())
                .s3Key(document.getS3Key())
                .originalName(document.getOriginalName())
                .contentType(document.getContentType())
                .uploadedByUserId(currentUser.getId())
                .documentHash(document.getDocumentHash())
                .build();

        documentEventProducer.publishDocumentProcessingEvent(kafkaMessage);
        log.info("Document {} queued for reprocessing by {}", documentId, currentUser.getUsername());

        return new ApiResponse<>(true, "Reprocessing started",
                "Document queued successfully", LocalDateTime.now().toString());
    }

    // ─── Get All (ADMIN) ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private Document findDocumentForUser(Long documentId, User currentUser) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (document.isDeleted()) {
            throw new ResourceNotFoundException("Document", "id", documentId);
        }

        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        boolean isOwner = Objects.equals(
                document.getUploadedBy().getId(), currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new ResourceNotFoundException("Document", "id", documentId);
        }

        return document;
    }

    private User getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();

        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "username", userDetails.getUsername()));
    }

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