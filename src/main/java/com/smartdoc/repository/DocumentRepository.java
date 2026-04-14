package com.smartdoc.repository;

import com.smartdoc.entity.Document;
import com.smartdoc.entity.DocumentStatus;
import com.smartdoc.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document,Long> {
    List<Document> findByUploadedBy(User user);
    List<Document> findByStatus(DocumentStatus status);
    List<Document> findByDocumentHash(String documentHash);

    // Custom JPQL query — note: uses Entity class name (Document), not table name
    @Query("SELECT d.status, COUNT(d) FROM Document d GROUP BY d.status")
    List<Object[]> countByStatus();
}
