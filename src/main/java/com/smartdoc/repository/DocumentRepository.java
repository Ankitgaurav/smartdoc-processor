package com.smartdoc.repository;

import com.smartdoc.entity.Document;
import com.smartdoc.entity.DocumentStatus;
import com.smartdoc.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByDocumentHash(String documentHash);
    List<Document> findByUploadedBy(String username);  // ← Spring resolves username field on User

    @Query(value = """
    SELECT * FROM documents d
    WHERE d.uploaded_by = :userId
    AND d.deleted = false
    AND (:status IS NULL OR d.status = :status)
    AND (CAST(:from AS timestamp) IS NULL OR d.created_at >= CAST(:from AS timestamp))
    AND (CAST(:to AS timestamp) IS NULL OR d.created_at <= CAST(:to AS timestamp))
    ORDER BY d.created_at DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM documents d
    WHERE d.uploaded_by = :userId
    AND d.deleted = false
    AND (:status IS NULL OR d.status = :status)
    AND (CAST(:from AS timestamp) IS NULL OR d.created_at >= CAST(:from AS timestamp))
    AND (CAST(:to AS timestamp) IS NULL OR d.created_at <= CAST(:to AS timestamp))
    """,
            nativeQuery = true)
    Page<Document> findByFilters(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("from")   String from,
            @Param("to")     String to,
            Pageable pageable
    );
}