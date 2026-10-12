package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.PageLimits;
import com.ees.common.PageResult;
import com.ees.config.EesProperties;
import com.ees.domain.DataBatch;
import com.ees.domain.DataSourceEntity;
import com.ees.domain.DataSourceField;
import com.ees.repository.DataSourceFieldRepository;
import com.ees.repository.DataSourceRepository;
import com.ees.util.CryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 外部数据源（甲方业务库）只读接入服务（方案 5.1 / 9.1）。
 *
 * <p>使用只读账号和受限查询：只允许单条 SELECT，禁止任何写操作关键字；
 * 设置连接超时、查询超时与行数上限；抽取过程写入审计。</p>
 */
@Service
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);
    private static final Pattern SELECT_START = Pattern.compile("^\\s*(select|with)\\b", Pattern.CASE_INSENSITIVE);

    /** 各数据库默认驱动类 */
    private static final Map<String, String> DRIVERS = Map.of(
            "MYSQL", "com.mysql.cj.jdbc.Driver",
            "POSTGRESQL", "org.postgresql.Driver",
            "ORACLE", "oracle.jdbc.OracleDriver",
            "SQLSERVER", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "DM", "dm.jdbc.driver.DmDriver",
            "KINGBASE", "com.kingbase8.Driver",
            "JDBC", ""
    );

    private final DataSourceRepository repository;
    private final DataSourceFieldRepository fieldRepository;
    private final DataBatchService dataBatchService;
    private final EesProperties properties;
    private final CodeService codeService;

    public DataSourceService(DataSourceRepository repository, DataSourceFieldRepository fieldRepository,
                             DataBatchService dataBatchService, EesProperties properties,
                             CodeService codeService) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.dataBatchService = dataBatchService;
        this.properties = properties;
        this.codeService = codeService;
    }

    @Transactional(readOnly = true)
    public PageResult<DataSourceEntity> search(String keyword, String dbType, int page, int size) {
        size = PageLimits.size(size);
        Page<DataSourceEntity> p = repository.search(blankToNull(keyword), blankToNull(dbType),
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        p.getContent().forEach(d -> d.setDbPassword(null));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DataSourceEntity get(Long id) {
        DataSourceEntity d = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.DATASOURCE_NOT_FOUND, "数据源不存在"));
        return d;
    }

    /** 返回给前端的脱敏视图。 */
    @Transactional(readOnly = true)
    public DataSourceEntity getMasked(Long id) {
        DataSourceEntity d = get(id);
        d.setDbPassword(d.getDbPassword() == null || d.getDbPassword().isBlank() ? null : "******");
        return d;
    }

    @Transactional
    public DataSourceEntity save(DataSourceEntity input, String operator) {
        DataSourceEntity ds;
        if (input.getId() != null) {
            ds = get(input.getId());
            if (input.getSourceCode() != null && !input.getSourceCode().equals(ds.getSourceCode())
                    && repository.existsBySourceCodeAndDeletedFalse(input.getSourceCode())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "数据源编码已存在：" + input.getSourceCode());
            }
        } else {
            if (input.getSourceCode() == null || input.getSourceCode().isBlank()) {
                input.setSourceCode(codeService.next("DS", "data_source", "source_code"));
            }
            if (repository.existsBySourceCodeAndDeletedFalse(input.getSourceCode())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "数据源编码已存在：" + input.getSourceCode());
            }
            ds = new DataSourceEntity();
            ds.setCreatedBy(operator);
        }
        ds.setSourceCode(input.getSourceCode());
        ds.setSourceName(input.getSourceName());
        ds.setDbType(input.getDbType() == null ? "MYSQL" : input.getDbType().toUpperCase());
        ds.setJdbcUrl(input.getJdbcUrl());
        ds.setDriverClass(input.getDriverClass() != null && !input.getDriverClass().isBlank()
                ? input.getDriverClass() : DRIVERS.getOrDefault(ds.getDbType(), ""));
        ds.setDbUsername(input.getDbUsername());
        // 密码：为空或掩码时保持原值
        if (input.getDbPassword() != null && !input.getDbPassword().isBlank()
                && !"******".equals(input.getDbPassword())) {
            ds.setDbPassword(CryptoUtils.encrypt(input.getDbPassword(), properties));
        } else if (ds.getDbPassword() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写数据源密码");
        }
        ds.setProjectId(input.getProjectId());
        ds.setReadOnly(true);
        ds.setMaxRows(input.getMaxRows() == null ? properties.getDatasourceConnector().getMaxRows() : input.getMaxRows());
        ds.setRemark(input.getRemark());
        ds.setUpdatedBy(operator);
        return repository.save(ds);
    }

    @Transactional
    public void delete(Long id, String operator) {
        DataSourceEntity ds = get(id);
        ds.setDeleted(true);
        ds.setUpdatedBy(operator);
        repository.save(ds);
        fieldRepository.deleteByDataSourceId(id);
    }

    /** 连接测试。 */
    @Transactional
    public Map<String, Object> testConnection(Long id) {
        DataSourceEntity ds = get(id);
        Map<String, Object> result = new LinkedHashMap<>();
        long begin = System.currentTimeMillis();
        try (Connection conn = open(ds, true)) {
            DatabaseMetaData md = conn.getMetaData();
            long cost = System.currentTimeMillis() - begin;
            result.put("success", true);
            result.put("productName", md.getDatabaseProductName());
            result.put("productVersion", md.getDatabaseProductVersion());
            result.put("driverName", md.getDriverName());
            result.put("driverVersion", md.getDriverVersion());
            result.put("url", md.getURL());
            result.put("readOnly", conn.isReadOnly());
            result.put("costMs", cost);
            result.put("message", "连接成功");
            ds.setStatus("OK");
            ds.setLastTestResult("连接成功：" + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败：" + rootMessage(e));
            result.put("costMs", System.currentTimeMillis() - begin);
            ds.setStatus("FAIL");
            ds.setLastTestResult(truncate("连接失败：" + rootMessage(e), 1900));
        }
        ds.setLastTestAt(LocalDateTime.now());
        repository.save(ds);
        return result;
    }

    /** 表清单。 */
    @Transactional
    public List<String> listTables(Long id, String schemaPattern) {
        DataSourceEntity ds = get(id);
        Set<String> tables = new LinkedHashSet<>();
        String[] types = {"TABLE", "VIEW"};
        try (Connection conn = open(ds, true)) {
            DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = schemaPattern != null && !schemaPattern.isBlank()
                    ? schemaPattern : conn.getSchema();
            try (ResultSet rs = md.getTables(catalog, schema, "%", types)) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            if (tables.isEmpty()) {
                try (ResultSet rs = md.getTables(null, null, "%", types)) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_QUERY_FAILED, "读取表清单失败：" + rootMessage(e));
        }
        List<String> list = new ArrayList<>(tables);
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    /** 字段预览（含样例值）。 */
    @Transactional
    public List<Map<String, Object>> previewFields(Long id, String tableName) {
        DataSourceEntity ds = get(id);
        List<Map<String, Object>> out = new ArrayList<>();
        if (tableName == null || tableName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要预览的表或视图");
        }
        if (!isSafeIdentifier(tableName)) {
            throw new BizException(ErrorCode.DATASOURCE_SQL_FORBIDDEN, "表名包含非法字符");
        }
        try (Connection conn = open(ds, true)) {
            DatabaseMetaData md = conn.getMetaData();
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = md.getColumns(conn.getCatalog(), conn.getSchema(), tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    String col = rs.getString("COLUMN_NAME");
                    columns.add(col);
                    m.put("columnName", col);
                    m.put("dataType", rs.getString("TYPE_NAME"));
                    m.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    m.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    m.put("ordinalPosition", rs.getInt("ORDINAL_POSITION"));
                    m.put("columnComment", safe(() -> rs.getString("REMARKS")));
                    out.add(m);
                }
            }
            // 取一行样例值
            if (!columns.isEmpty()) {
                String sql = "SELECT * FROM " + tableName + " LIMIT 1";
                if ("ORACLE".equalsIgnoreCase(ds.getDbType()) || "DM".equalsIgnoreCase(ds.getDbType())) {
                    sql = "SELECT * FROM " + tableName + " WHERE ROWNUM <= 1";
                } else if ("SQLSERVER".equalsIgnoreCase(ds.getDbType())) {
                    sql = "SELECT TOP 1 * FROM " + tableName;
                }
                try (Statement st = conn.createStatement()) {
                    st.setQueryTimeout(properties.getDatasourceConnector().getQueryTimeoutSeconds());
                    try (ResultSet rs = st.executeQuery(sql)) {
                        if (rs.next()) {
                            for (Map<String, Object> m : out) {
                                Object v = rs.getObject(String.valueOf(m.get("columnName")));
                                m.put("sampleValue", v == null ? null : truncate(String.valueOf(v), 200));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("样例值读取失败: {}", e.getMessage());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_QUERY_FAILED, "字段预览失败：" + rootMessage(e));
        }
        return out;
    }

    /** 按受限配置抽取数据并生成数据批次。 */
    @Transactional
    public Map<String, Object> extract(Long id, String querySql, Long projectId, Long targetVersionId,
                                       String operator) {
        DataSourceEntity ds = get(id);
        String sql = validateReadOnlySql(querySql);
        int maxRows = ds.getMaxRows() == null ? properties.getDatasourceConnector().getMaxRows() : ds.getMaxRows();

        List<Map<String, String>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        int truncated = 0;
        try (Connection conn = open(ds, true);
             PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY,
                     ResultSet.CONCUR_READ_ONLY)) {
            ps.setQueryTimeout(properties.getDatasourceConnector().getQueryTimeoutSeconds());
            ps.setFetchSize(1000);
            ps.setMaxRows(maxRows + 1);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    headers.add(md.getColumnLabel(i));
                }
                int count = 0;
                while (rs.next()) {
                    if (count >= maxRows) {
                        truncated = 1;
                        break;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        Object v = rs.getObject(i);
                        row.put(md.getColumnLabel(i), v == null ? null : String.valueOf(v));
                    }
                    rows.add(row);
                    count++;
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_QUERY_FAILED, "数据抽取失败：" + rootMessage(e));
        }

        DataBatch batch = dataBatchService.createBatchFromRows(rows, projectId, id,
                "数据源 " + ds.getSourceName() + " 抽取", sql, targetVersionId, operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSourceId", id);
        result.put("batchId", batch.getId());
        result.put("batchCode", batch.getBatchCode());
        result.put("headers", headers);
        result.put("extractedRows", rows.size());
        result.put("truncated", truncated == 1);
        result.put("maxRows", maxRows);
        result.put("status", batch.getStatus());
        result.put("validRows", batch.getValidRows());
        result.put("errorRows", batch.getErrorRows());
        result.put("preview", rows.size() > 20 ? rows.subList(0, 20) : rows);
        log.info("数据源 {} 抽取完成，批次 {} 行数 {}", ds.getSourceCode(), batch.getBatchCode(), rows.size());
        return result;
    }

    /** 校验 SQL 只读：仅允许单条 SELECT / WITH，禁止写操作关键字。 */
    public String validateReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 不能为空");
        }
        String s = sql.trim();
        // 去掉结尾分号
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.contains(";")) {
            throw new BizException(ErrorCode.DATASOURCE_SQL_FORBIDDEN, "只允许提交单条 SQL 语句");
        }
        if (!SELECT_START.matcher(s).find()) {
            throw new BizException(ErrorCode.DATASOURCE_SQL_FORBIDDEN, "只允许 SELECT 查询");
        }
        String lower = s.toLowerCase();
        for (String kw : properties.getDatasourceConnector().forbiddenKeywordList()) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(kw) + "\\b");
            if (p.matcher(lower).find()) {
                throw new BizException(ErrorCode.DATASOURCE_SQL_FORBIDDEN,
                        "SQL 含有禁止的写操作关键字：" + kw.toUpperCase());
            }
        }
        // 禁止注释
        if (lower.contains("--") || lower.contains("/*")) {
            throw new BizException(ErrorCode.DATASOURCE_SQL_FORBIDDEN, "SQL 不允许包含注释");
        }
        return s;
    }

    private Connection open(DataSourceEntity ds, boolean readOnly) throws Exception {
        String pwd = CryptoUtils.decrypt(ds.getDbPassword(), properties);
        Properties props = new Properties();
        props.put("user", ds.getDbUsername());
        props.put("password", pwd == null ? "" : pwd);
        props.put("connectTimeout", String.valueOf(properties.getDatasourceConnector().getConnectTimeoutSeconds() * 1000));
        if (ds.getDriverClass() != null && !ds.getDriverClass().isBlank()) {
            try {
                Class.forName(ds.getDriverClass());
            } catch (ClassNotFoundException e) {
                throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAILED,
                        "驱动类未找到：" + ds.getDriverClass() + "，请确认已将该数据库驱动加入部署包");
            }
        }
        Connection conn = DriverManager.getConnection(ds.getJdbcUrl(), props);
        if (readOnly) {
            try {
                conn.setReadOnly(true);
            } catch (Exception ignored) {
                // 部分驱动不支持，忽略
            }
        }
        return conn;
    }

    private static boolean isSafeIdentifier(String s) {
        return s.matches("[A-Za-z0-9_$.\\[\\]\"`]+");
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private interface Supplied {
        String get() throws Exception;
    }

    private static String safe(Supplied s) {
        try {
            return s.get();
        } catch (Exception e) {
            return null;
        }
    }
}
