package com.ees.repository;

import com.ees.domain.SnapshotValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SnapshotValueRepository extends JpaRepository<SnapshotValue, Long> {

    List<SnapshotValue> findBySnapshotIdOrderByObjectCodeAscIndicatorCodeAsc(Long snapshotId);

    List<SnapshotValue> findBySnapshotIdAndObjectCodeOrderByIndicatorCodeAsc(Long snapshotId, String objectCode);

    @Query("select distinct v.objectCode from SnapshotValue v where v.snapshotId = :snapshotId order by v.objectCode")
    List<String> findObjectCodes(@Param("snapshotId") Long snapshotId);

    @Query("select distinct v.indicatorCode from SnapshotValue v where v.snapshotId = :snapshotId order by v.indicatorCode")
    List<String> findIndicatorCodes(@Param("snapshotId") Long snapshotId);

    long countBySnapshotId(Long snapshotId);

    long countBySnapshotIdAndMissingTrue(Long snapshotId);

    long countBySnapshotIdAndOutlierTrue(Long snapshotId);

    @Modifying
    @Query("delete from SnapshotValue v where v.snapshotId = :snapshotId")
    void deleteBySnapshotId(@Param("snapshotId") Long snapshotId);
}
