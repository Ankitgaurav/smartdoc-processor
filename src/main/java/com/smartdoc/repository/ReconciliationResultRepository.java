package com.smartdoc.repository;

import com.smartdoc.entity.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

    Optional<ReconciliationResult> findByDocumentId(Long documentId);

    // Spring Data generates all 3 queries automatically from method names
    long countByMatched(Boolean matched);     // pass true or false

    // We will use these in ReconciliationService like:
    // long total     = repo.count();
    // long matched   = repo.countByMatched(true);
    // long mismatched = repo.countByMatched(false);
}