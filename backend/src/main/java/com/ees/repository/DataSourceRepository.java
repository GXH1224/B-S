package com.ees.repository;

import com.ees.domain.DataSourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {

    Optional<DataSourceEntity> findBySourceCodeAndDeletedFalse(String sourceCode);

    Optional<DataSourceEntity> findByIdAndDeletedFalse(Long id);

    boolean existsBySourceCodeAndDeletedFalse(String sourceCode);

    List<DataSourceEntity> findByDeletedFalseOrderByIdDesc();

    @Query("""
            select d from DataSourceEntity d where d.deleted = false
              and (:keyword is null or d.sourceName like %:keyword% or d.sourceCode like %:keyword%)
              and (:dbType is null or d.dbType = :dbType)
            """)
    Page<DataSourceEntity> search(@Param("keyword") String keyword,
                                  @Param("dbType") String dbType,
                                  Pageable pageable);
}
