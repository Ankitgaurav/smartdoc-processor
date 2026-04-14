package com.smartdoc.repository;

import com.smartdoc.entity.ReferenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReferenceRecordRepository extends JpaRepository<ReferenceRecord, Long> {

    Optional<ReferenceRecord> findByVendorNameIgnoreCase(String vendorName);
    Optional<ReferenceRecord> findByReferenceId(String referenceId);
}