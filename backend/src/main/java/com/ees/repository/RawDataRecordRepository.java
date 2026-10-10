package com.ees.repository;

import com.ees.domain.RawDataRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RawDataRecordRepository extends JpaRepository<RawDataRecord, Long> {

    List<RawDataRecord> findByBatchIdOrderByRowNoAsc(Long batchId);

    List<RawDataRecord> findByBatchIdAndValidFalseOrderByRowNoAsc(Long batchId);

    @Query("select distinct r.objectCode from RawDataRecord r where r.batchId = :batchId and r.objectCode is not null")
    List<String> findObjectCodes(@Param("batchId") Long batchId);

    @Query("select distinct r.indicatorCode from RawDataRecord r where r.batchId = :batchId and r.indicatorCode is not null")
    List<String> findIndicatorCodes(@Param("batchId") Long batchId);

    long countByBatchId(Long batchId);

    long countByBatchIdAndValidFalse(Long batchId);

    @Modifying
    @Query("delete from RawDataRecord r where r.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);
}
