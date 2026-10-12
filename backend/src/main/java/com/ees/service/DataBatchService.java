package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageLimits;
import com.ees.common.PageResult;
import com.ees.config.EesProperties;
import com.ees.domain.DataBatch;
import com.ees.domain.DataQualityRule;
import com.ees.domain.Indicator;
import com.ees.domain.RawDataRecord;
import com.ees.repository.DataBatchRepository;
import com.ees.repository.DataQualityRuleRepository;
import com.ees.repository.RawDataRecordRepository;
import com.ees.util.TabularReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据批次接入服务（方案 4.4 / 5.1 / 5.3）。
 *
 * <p>支持文件导入（XLSX / CSV）与数据库抽取结果入库，统一生成数据批次、质量报告和错误明细。</p>
 */
@Service
public class DataBatchService {

    private static final Logger log = LoggerFactory.getLogger(DataBatchService.class);

    /** 标准字段（附录 A） */
    public static final List<String> TEMPLATE_COLUMNS = List.of(
            "project_code", "object_code", "object_name", "data_time",
            "indicator_code", "indicator_value", "unit", "source_batch", "remark");

    private final DataBatchRepository batchRepository;
    private final RawDataRecordRepository rawRepository;
    private final DataQualityRuleRepository ruleRepository;
    private final IndicatorService indicatorService;
    private final com.ees.repository.ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final EesProperties properties;
    private final CodeService codeService;

    public DataBatchService(DataBatchRepository batchRepository, RawDataRecordRepository rawRepository,
                            DataQualityRuleRepository ruleRepository, IndicatorService indicatorService,
                            com.ees.repository.ProjectRepository projectRepository,
                            ProjectService projectService,
                            EesProperties properties,
                            CodeService codeService) {
        this.batchRepository = batchRepository;
        this.rawRepository = rawRepository;
        this.ruleRepository = ruleRepository;
        this.indicatorService = indicatorService;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.properties = properties;
        this.codeService = codeService;
    }

    @Transactional(readOnly = true)
    public PageResult<DataBatch> search(Long projectId, String sourceType, String status,
                                        String keyword, int page, int size) {
        size = PageLimits.size(size);
        // 数据权限：null = 不限制（管理员 / ALL 范围）；空集合 = 无可见项目，直接返回空页
        List<Long> ids = projectService.visibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<DataBatch> p = batchRepository.search(projectId, ids, blankToNull(sourceType), blankToNull(status),
                blankToNull(keyword), PageRequest.of(Math.max(0, page - 1), size,
                        Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DataBatch get(Long id) {
        DataBatch batch = batchRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BizException.notFound("数据批次不存在"));
        // 数据权限：批次所属项目必须对当前账号可见（否则不能按 id 直读，包括其记录/错误/质量报告）
        projectService.checkVisible(batch.getProjectId());
        return batch;
    }

    /** 数据权限守卫：按批次 id 取数据（记录 / 错误明细 / 质量报告 / 重新校验 / 删除）前先确认其所属项目可见。 */
    private void requireBatchVisible(Long batchId) {
        if (batchId == null) {
            return;
        }
        batchRepository.findByIdAndDeletedFalse(batchId)
                .ifPresent(b -> projectService.checkVisible(b.getProjectId()));
    }

    @Transactional(readOnly = true)
    public List<RawDataRecord> records(Long batchId, int limit) {
        requireBatchVisible(batchId);
        List<RawDataRecord> all = rawRepository.findByBatchIdOrderByRowNoAsc(batchId);
        if (limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> errorRows(Long batchId) {
        requireBatchVisible(batchId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RawDataRecord r : rawRepository.findByBatchIdAndValidFalseOrderByRowNoAsc(batchId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rowNo", r.getRowNo());
            m.put("projectCode", r.getProjectCode());
            m.put("objectCode", r.getObjectCode());
            m.put("dataTime", r.getDataTime());
            m.put("indicatorCode", r.getIndicatorCode());
            m.put("indicatorValue", r.getIndicatorValueText() != null ? r.getIndicatorValueText() : r.getIndicatorValue());
            m.put("unit", r.getUnit());
            m.put("errorMsg", r.getErrorMsg());
            out.add(m);
        }
        return out;
    }

    /**
     * 文件导入：保存原始文件、解析、校验、登记批次与原始记录。
     *
     * @param targetVersionId 校验依据的指标体系版本，可为空
     */
    @Transactional
    public Map<String, Object> importFile(MultipartFile file, Long projectId, Long targetVersionId,
                                          String operator) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要导入的数据文件");
        }
        // 数据权限：只能往自己可见的项目里导入数据
        if (projectId != null) {
            projectService.checkVisible(projectId);
        }
        String fileName = file.getOriginalFilename();
        validateExtension(fileName);

        Path stored = storeFile(file, "batch");
        TabularReader.TableData data;
        try (InputStream in = file.getInputStream()) {
            data = TabularReader.read(in, fileName);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "文件解析失败：" + e.getMessage(), e);
        }
        if (data.size() == 0) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "文件中没有数据行");
        }
        if (!data.hasColumn("indicator_code") && !data.hasColumn("指标编码")) {
            throw new BizException(ErrorCode.DATA_FIELD_MISSING,
                    "缺少必需列 indicator_code（指标编码），请参考标准数据模板");
        }

        String batchCode = codeService.next("BATCH", "data_batch", "batch_code");
        DataBatch batch = new DataBatch();
        batch.setBatchCode(batchCode);
        batch.setProjectId(projectId);
        batch.setProjectCode(projectCodeOf(projectId));
        batch.setSourceType("FILE");
        batch.setSourceRef(fileName);
        batch.setFileName(fileName);
        batch.setFileSize(file.getSize());
        batch.setStoredPath(stored == null ? null : stored.toString());
        batch.setFileHash(sha256(file));
        batch.setTargetVersionId(targetVersionId);
        batch.setCreatedBy(operator);
        batch.setCreatedAt(LocalDateTime.now());
        batchRepository.save(batch);

        Map<String, Indicator> indicatorIndex = targetVersionId == null
                ? new LinkedHashMap<>() : indicatorService.codeIndex(targetVersionId);

        ValidationOutcome outcome = validate(data, indicatorIndex, targetVersionId);

        // 落库原始记录
        List<RawDataRecord> records = new ArrayList<>(outcome.records.size());
        for (RawDataRecord r : outcome.records) {
            r.setBatchId(batch.getId());
            records.add(r);
        }
        rawRepository.saveAll(records);

        batch.setTotalRows(data.size());
        batch.setValidRows(outcome.validRows);
        batch.setErrorRows(outcome.errorRows);
        batch.setObjectCount(outcome.objectCount);
        batch.setIndicatorCount(outcome.indicatorCount);
        batch.setQualityReport(JsonUtils.toJson(outcome.qualityReport));
        batch.setErrorDetail(JsonUtils.toJson(outcome.errors.size() > 500
                ? outcome.errors.subList(0, 500) : outcome.errors));
        batch.setStatus(outcome.blocking ? "FAILED" : "VALIDATED");
        batchRepository.save(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batch.getId());
        result.put("batchCode", batch.getBatchCode());
        result.put("totalRows", batch.getTotalRows());
        result.put("validRows", batch.getValidRows());
        result.put("errorRows", batch.getErrorRows());
        result.put("objectCount", batch.getObjectCount());
        result.put("indicatorCount", batch.getIndicatorCount());
        result.put("status", batch.getStatus());
        result.put("qualityReport", outcome.qualityReport);
        result.put("errors", outcome.errors.size() > 200 ? outcome.errors.subList(0, 200) : outcome.errors);
        result.put("blocking", outcome.blocking);
        log.info("数据批次导入完成 {} 行数={} 有效={} 错误={}", batchCode, batch.getTotalRows(),
                batch.getValidRows(), batch.getErrorRows());
        return result;
    }

    /** 数据库抽取结果入库（由 DataSourceService 调用）。 */
    @Transactional
    public DataBatch createBatchFromRows(List<Map<String, String>> rows, Long projectId, Long dataSourceId,
                                         String sourceRef, String querySql, Long targetVersionId,
                                         String operator) {
        // 数据权限：只能往自己可见的项目里写入数据
        if (projectId != null) {
            projectService.checkVisible(projectId);
        }
        TabularReader.TableData data = new TabularReader.TableData();
        if (!rows.isEmpty()) {
            data.getHeaders().addAll(rows.get(0).keySet());
        }
        int i = 1;
        for (Map<String, String> r : rows) {
            data.getRows().add(r);
            data.getRowNumbers().add(++i);
        }

        DataBatch batch = new DataBatch();
        batch.setBatchCode(codeService.next("BATCH", "data_batch", "batch_code"));
        batch.setProjectId(projectId);
        batch.setProjectCode(projectCodeOf(projectId));
        batch.setSourceType("DATABASE");
        batch.setSourceRef(sourceRef);
        batch.setDataSourceId(dataSourceId);
        batch.setQuerySql(querySql);
        batch.setTargetVersionId(targetVersionId);
        batch.setCreatedBy(operator);
        batch.setCreatedAt(LocalDateTime.now());
        batchRepository.save(batch);

        Map<String, Indicator> indicatorIndex = targetVersionId == null
                ? new LinkedHashMap<>() : indicatorService.codeIndex(targetVersionId);
        ValidationOutcome outcome = validate(data, indicatorIndex, targetVersionId);
        for (RawDataRecord r : outcome.records) {
            r.setBatchId(batch.getId());
        }
        rawRepository.saveAll(outcome.records);

        batch.setTotalRows(data.size());
        batch.setValidRows(outcome.validRows);
        batch.setErrorRows(outcome.errorRows);
        batch.setObjectCount(outcome.objectCount);
        batch.setIndicatorCount(outcome.indicatorCount);
        batch.setQualityReport(JsonUtils.toJson(outcome.qualityReport));
        batch.setErrorDetail(JsonUtils.toJson(outcome.errors.size() > 500
                ? outcome.errors.subList(0, 500) : outcome.errors));
        batch.setStatus(outcome.blocking ? "FAILED" : "VALIDATED");
        return batchRepository.save(batch);
    }

    /** 校验主体：按方案 5.3 数据质量规则执行。 */
    private ValidationOutcome validate(TabularReader.TableData data,
                                       Map<String, Indicator> indicatorIndex,
                                       Long targetVersionId) {
        ValidationOutcome out = new ValidationOutcome();
        List<DataQualityRule> rules = ruleRepository.findByEnabledTrueOrderByIdAsc();
        Map<String, String> severityByType = new HashMap<>();
        for (DataQualityRule r : rules) {
            severityByType.put(r.getRuleType(), r.getSeverity());
        }
        String requiredSeverity = severityByType.getOrDefault("REQUIRED", "BLOCK");
        String formatSeverity = severityByType.getOrDefault("FORMAT", "BLOCK");
        String rangeSeverity = severityByType.getOrDefault("RANGE", "WARN");
        String duplicateSeverity = severityByType.getOrDefault("DUPLICATE", "WARN");
        String missingSeverity = severityByType.getOrDefault("MISSING", "BLOCK");
        String unitSeverity = severityByType.getOrDefault("UNIT", "INFO");

        Set<String> objects = new LinkedHashSet<>();
        Set<String> indicatorsSeen = new LinkedHashSet<>();
        Set<String> duplicateKeys = new LinkedHashSet<>();
        int missingCount = 0;
        int outlierCount = 0;
        int rangeWarn = 0;
        int unitWarn = 0;

        for (int idx = 0; idx < data.size(); idx++) {
            Map<String, String> row = data.getRows().get(idx);
            int lineNo = data.getRowNumbers().get(idx);
            RawDataRecord rec = new RawDataRecord();
            rec.setRowNo(lineNo);
            StringBuilder errMsg = new StringBuilder();
            boolean valid = true;

            String projectCode = data.get(row, "project_code", "项目编码");
            String objectCode = data.get(row, "object_code", "对象编码", "object");
            String objectName = data.get(row, "object_name", "对象名称");
            String dataTimeRaw = data.get(row, "data_time", "数据时间", "时间");
            String indicatorCode = data.get(row, "indicator_code", "指标编码");
            String valueRaw = data.get(row, "indicator_value", "指标值", "value");
            String unit = data.get(row, "unit", "单位");
            String sourceBatch = data.get(row, "source_batch", "来源批次");
            String remark = data.get(row, "remark", "备注");

            rec.setProjectCode(projectCode);
            rec.setObjectCode(objectCode);
            rec.setObjectName(objectName);
            rec.setDataTime(TabularReader.parseDateTime(dataTimeRaw));
            rec.setIndicatorCode(indicatorCode);
            rec.setIndicatorValueText(valueRaw);
            rec.setUnit(unit);
            rec.setSourceBatch(sourceBatch);
            rec.setRemark(remark);

            // 必填字段校验（方案 5.3：必填字段缺失时阻止任务提交）
            if (objectCode == null || objectCode.isBlank()) {
                appendErr(errMsg, "对象编码(object_code)缺失");
                out.errors.add(error(lineNo, "object_code", "对象编码缺失", requiredSeverity));
                if ("BLOCK".equals(requiredSeverity)) {
                    out.requiredFieldMissing++;
                }
                valid = false;
            }
            if (indicatorCode == null || indicatorCode.isBlank()) {
                appendErr(errMsg, "指标编码(indicator_code)缺失");
                out.errors.add(error(lineNo, "indicator_code", "指标编码缺失", requiredSeverity));
                if ("BLOCK".equals(requiredSeverity)) {
                    out.requiredFieldMissing++;
                }
                valid = false;
            }
            if (valueRaw == null || valueRaw.isBlank()) {
                // 缺失值：按缺失值策略处理（方案 5.3 缺失值不允许静默处理）
                appendErr(errMsg, "指标值(indicator_value)缺失");
                out.errors.add(error(lineNo, "indicator_value", "指标值缺失（未按缺失值策略静默处理）", missingSeverity));
                missingCount++;
                out.missingValues++;
                valid = false;
            } else {
                Double v = TabularReader.parseNumber(valueRaw);
                if (v == null) {
                    // 数值格式非法：标记该行为错误并提供修正建议，不影响批次内其他有效行
                    appendErr(errMsg, "指标值非数值：" + valueRaw);
                    out.errors.add(error(lineNo, "indicator_value",
                            "数值格式非法：" + valueRaw + "（建议修正为数值后重新导入该行）", formatSeverity));
                    out.formatErrors++;
                    valid = false;
                } else {
                    rec.setIndicatorValue(BigDecimal.valueOf(v));
                }
            }
            if (dataTimeRaw != null && !dataTimeRaw.isBlank() && rec.getDataTime() == null) {
                out.errors.add(error(lineNo, "data_time", "时间格式无法解析：" + dataTimeRaw, "WARN"));
            }

            // 指标覆盖与范围校验
            Indicator ind = indicatorCode == null ? null : indicatorIndex.get(indicatorCode);
            if (ind != null) {
                rec.setIndicatorName(ind.getIndicatorName());
                if (rec.getUnit() == null || rec.getUnit().isBlank()) {
                    rec.setUnit(ind.getUnit());
                }
                if (rec.getIndicatorValue() != null) {
                    double v = rec.getIndicatorValue().doubleValue();
                    if (ind.getMinValue() != null && v < ind.getMinValue().doubleValue()) {
                        out.errors.add(error(lineNo, "indicator_value",
                                String.format("指标 %s 值 %.4f 低于下界 %.4f", indicatorCode, v,
                                        ind.getMinValue().doubleValue()), rangeSeverity));
                        rangeWarn++;
                    }
                    if (ind.getMaxValue() != null && v > ind.getMaxValue().doubleValue()) {
                        out.errors.add(error(lineNo, "indicator_value",
                                String.format("指标 %s 值 %.4f 高于上界 %.4f", indicatorCode, v,
                                        ind.getMaxValue().doubleValue()), rangeSeverity));
                        rangeWarn++;
                    }
                }
                if (rec.getUnit() != null && ind.getUnit() != null && !rec.getUnit().isBlank()
                        && !ind.getUnit().equals(rec.getUnit())) {
                    out.errors.add(error(lineNo, "unit",
                            "单位与指标定义不一致：数据为 " + rec.getUnit() + "，定义为 " + ind.getUnit(), unitSeverity));
                    unitWarn++;
                }
            } else if (targetVersionId != null && indicatorCode != null) {
                out.errors.add(error(lineNo, "indicator_code",
                        "指标 " + indicatorCode + " 不在所选指标体系版本中", requiredSeverity));
                valid = false;
            }

            // 重复记录校验（批次 + 对象 + 时间 + 指标编码）
            String key = objectCode + "|" + (rec.getDataTime() == null ? "" : rec.getDataTime().toString())
                    + "|" + indicatorCode;
            if (!duplicateKeys.add(key)) {
                out.errors.add(error(lineNo, "indicator_code",
                        "重复记录：" + objectCode + " / " + indicatorCode + " / " + rec.getDataTime(),
                        duplicateSeverity));
                if ("BLOCK".equals(duplicateSeverity)) {
                    valid = false;
                }
            }

            if (objectCode != null) {
                objects.add(objectCode);
            }
            if (indicatorCode != null) {
                indicatorsSeen.add(indicatorCode);
            }
            rec.setValid(valid);
            rec.setErrorMsg(errMsg.length() == 0 ? null : truncate(errMsg.toString(), 480));
            if (valid) {
                out.validRows++;
            } else {
                out.errorRows++;
            }
            out.records.add(rec);
        }

        // 指标覆盖校验
        if (targetVersionId != null) {
            Map<String, Indicator> versionIndex = indicatorService.codeIndex(targetVersionId);
            List<String> missingIndicators = new ArrayList<>();
            for (Indicator i : versionIndex.values()) {
                if (Boolean.TRUE.equals(i.getLeaf()) && !indicatorsSeen.contains(i.getIndicatorCode())) {
                    missingIndicators.add(i.getIndicatorCode());
                }
            }
            if (!missingIndicators.isEmpty()) {
                out.errors.add(error(0, "indicator_code",
                        "数据未覆盖必需指标：" + String.join(", ", missingIndicators.size() > 20
                                ? missingIndicators.subList(0, 20) : missingIndicators)
                                + (missingIndicators.size() > 20 ? " 等 " + missingIndicators.size() + " 项" : ""),
                        requiredSeverity));
                if ("BLOCK".equals(requiredSeverity)) {
                    out.indicatorNotCovered = true;
                }
            }
        }

        // 方向未确认时不得执行需要标准化的算法（方案 5.3）
        for (String code : indicatorsSeen) {
            Indicator ind = indicatorIndex.get(code);
            if (ind != null && (ind.getDirection() == null || ind.getDirection().isBlank())) {
                out.directionUnknown = true;
                out.errors.add(error(0, "direction",
                        "指标 " + code + " 未确认正负方向，不得执行需要标准化的算法", "WARN"));
                break;
            }
        }

        // ---- 阻断判定 ----
        // 方案 5.3：只有“必填字段缺失 / 缺失值（按策略阻止）/ 指标未覆盖”才阻止任务提交；
        // 数值格式非法、越界、重复、单位不一致等按行标记错误或告警，不影响批次内其他有效数据。
        List<String> blockingReasons = new ArrayList<>();
        if (out.requiredFieldMissing > 0) {
            blockingReasons.add("存在 " + out.requiredFieldMissing + " 处必填字段缺失（object_code / indicator_code）");
        }
        if (out.missingValues > 0) {
            if ("BLOCK".equals(missingSeverity)) {
                blockingReasons.add("存在 " + out.missingValues + " 处缺失值，当前缺失值策略为阻止计算");
            } else if ("DROP".equals(missingSeverity)) {
                blockingReasons.add("存在 " + out.missingValues + " 处缺失值，已按策略剔除对应记录");
            }
        }
        if (out.indicatorNotCovered) {
            blockingReasons.add("数据未覆盖指标体系必需指标");
        }
        if (out.directionUnknown) {
            blockingReasons.add("存在指标未确认正负方向，不得执行需要标准化的算法");
        }
        out.blocking = !blockingReasons.isEmpty();

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("totalRows", data.size());
        quality.put("validRows", out.validRows);
        quality.put("errorRows", out.errorRows);
        quality.put("objectCount", objects.size());
        quality.put("distinctIndicatorCount", indicatorsSeen.size());
        quality.put("missingValueCount", missingCount);
        quality.put("outOfRangeCount", rangeWarn);
        quality.put("unitMismatchCount", unitWarn);
        quality.put("formatErrorCount", out.formatErrors);
        quality.put("requiredFieldMissingCount", out.requiredFieldMissing);
        quality.put("duplicateCount", out.errors.stream()
                .filter(e -> String.valueOf(e.get("message")).startsWith("重复记录")).count());
        quality.put("blocking", out.blocking);
        quality.put("blockingReasons", blockingReasons);
        quality.put("checkedAt", LocalDateTime.now().toString());
        quality.put("ruleSeverities", severityByType);
        out.qualityReport = quality;
        out.objectCount = objects.size();
        out.indicatorCount = indicatorsSeen.size();
        return out;
    }

    @Transactional
    public DataBatch saveBatch(DataBatch batch) {
        return batchRepository.save(batch);
    }

    @Transactional
    public DataBatch revalidate(Long batchId) {
        requireBatchVisible(batchId);
        DataBatch batch = get(batchId);
        TabularReader.TableData data = new TabularReader.TableData();
        List<RawDataRecord> recs = rawRepository.findByBatchIdOrderByRowNoAsc(batchId);
        data.getHeaders().addAll(TEMPLATE_COLUMNS);
        for (RawDataRecord r : recs) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("project_code", r.getProjectCode());
            m.put("object_code", r.getObjectCode());
            m.put("object_name", r.getObjectName());
            m.put("data_time", r.getDataTime() == null ? null : r.getDataTime().toString());
            m.put("indicator_code", r.getIndicatorCode());
            m.put("indicator_value", r.getIndicatorValue() == null ? null : r.getIndicatorValue().toPlainString());
            m.put("unit", r.getUnit());
            m.put("source_batch", r.getSourceBatch());
            m.put("remark", r.getRemark());
            data.getRows().add(m);
            data.getRowNumbers().add(r.getRowNo() == null ? 0 : r.getRowNo());
        }
        Map<String, Indicator> indicatorIndex = batch.getTargetVersionId() == null
                ? new LinkedHashMap<>() : indicatorService.codeIndex(batch.getTargetVersionId());
        rawRepository.deleteByBatchId(batchId);
        ValidationOutcome outcome = validate(data, indicatorIndex, batch.getTargetVersionId());
        for (RawDataRecord r : outcome.records) {
            r.setBatchId(batchId);
        }
        rawRepository.saveAll(outcome.records);
        batch.setValidRows(outcome.validRows);
        batch.setErrorRows(outcome.errorRows);
        batch.setObjectCount(outcome.objectCount);
        batch.setIndicatorCount(outcome.indicatorCount);
        batch.setQualityReport(JsonUtils.toJson(outcome.qualityReport));
        batch.setErrorDetail(JsonUtils.toJson(outcome.errors.size() > 500
                ? outcome.errors.subList(0, 500) : outcome.errors));
        batch.setStatus(outcome.blocking ? "FAILED" : "VALIDATED");
        return batchRepository.save(batch);
    }

    @Transactional
    public void delete(Long batchId, String operator) {
        requireBatchVisible(batchId);
        DataBatch batch = get(batchId);
        rawRepository.deleteByBatchId(batchId);
        batch.setDeleted(true);
        batch.setStatus("FAILED");
        batchRepository.save(batch);
        log.info("删除数据批次 {} by {}", batch.getBatchCode(), operator);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> qualityReport(Long batchId) {
        requireBatchVisible(batchId);
        DataBatch b = get(batchId);
        Map<String, Object> m = new LinkedHashMap<>(JsonUtils.toMap(b.getQualityReport()));
        m.put("batchId", b.getId());
        m.put("batchCode", b.getBatchCode());
        m.put("status", b.getStatus());
        m.put("errors", JsonUtils.toMapList(b.getErrorDetail()));
        return m;
    }

    /** 把上传文件落盘，用于留档与追溯。 */
    public Path storeFile(MultipartFile file, String subDir) {
        try {
            String root = properties.getStorage().getUploadDir();
            Path dir = Paths.get(root, subDir, LocalDateTime.now().toLocalDate().toString())
                    .toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = System.currentTimeMillis() + "_" + sanitize(file.getOriginalFilename());
            Path target = dir.resolve(name);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            log.warn("保存上传文件失败: {}", e.getMessage());
            return null;
        }
    }

    private void validateExtension(String fileName) {
        if (fileName == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名无效");
        }
        int idx = fileName.lastIndexOf('.');
        String ext = idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase();
        if (!properties.getStorage().allowedExtensionList().contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不允许的文件类型 ." + ext + "，允许：" + properties.getStorage().getAllowedExtensions());
        }
    }

    private static String sanitize(String name) {
        if (name == null) {
            return "upload";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void appendErr(StringBuilder sb, String msg) {
        if (sb.length() > 0) {
            sb.append("；");
        }
        sb.append(msg);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Map<String, Object> error(int row, String field, String message, String severity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("field", field);
        m.put("message", message);
        m.put("severity", severity);
        return m;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 取项目编码写入批次，保证批次、快照、任务的项目编码一致可追溯。 */
    private String projectCodeOf(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId).map(p -> p.getProjectCode()).orElse(null);
    }

    /** 校验中间结果。 */
    private static class ValidationOutcome {
        final List<RawDataRecord> records = new ArrayList<>();
        final List<Map<String, Object>> errors = new ArrayList<>();
        Map<String, Object> qualityReport = new LinkedHashMap<>();
        int validRows;
        int errorRows;
        int objectCount;
        int indicatorCount;
        boolean blocking;
        int requiredFieldMissing;
        int missingValues;
        int formatErrors;
        boolean indicatorNotCovered;
        boolean directionUnknown;
    }
}
