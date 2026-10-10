package com.ees.repository;

import com.ees.domain.DataSourceField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceFieldRepository extends JpaRepository<DataSourceField, Long> {

    List<DataSourceField> findByDataSourceIdOrderByTableNameAscOrdinalPositionAsc(Long dataSourceId);

    List<DataSourceField> findByDataSourceIdAndTableNameOrderByOrdinalPositionAsc(Long dataSourceId, String tableName);

    void deleteByDataSourceId(Long dataSourceId);
}
