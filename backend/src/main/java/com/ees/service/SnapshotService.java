package com.ees.service;

import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.MathSupport;
import com.ees.algorithm.ScoringSupport;
import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageLimits;
import com.ees.common.PageResult;
import com.ees.domain.DataBatch;
import com.ees.domain.DataSnapshot;
import com.ees.domain.Indicator;
import com.ees.domain.IndicatorVersion;
import com.ees.domain.RawDataRecord;
import com.ees.domain.SnapshotValue;
import com.ees.repository.DataSnapshotRepository;
import com.ees.repository.RawDataRecordRepository;
import com.ees.repository.SnapshotValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据快照服务（方案 5.2 / 5.4）。
 *
 * <p>生成评估数据快照后在任务执行完成后保持不变，历史结果不因源数据变化而被覆盖。
 * 原始数据保存于 raw_data_record，标准化数据保存于 snapshot_value，两者分离。</p>
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final DataSnapshotRepository snapshotRepository;
    private final SnapshotValueRepository valueRepository;
    private final RawDataRecordRepository rawRepository;
    private final DataBatchService batchService;
    private final IndicatorService indicatorService;
    private final ProjectService projectService;
    private final CodeService codeService;

    public SnapshotService(DataSnapshotRepository snapshotRepository, SnapshotValueRepository valueRepository,
                           RawDataRecordRepository rawRepository, DataBatchService batchService,
                           IndicatorService indicatorService,
                           ProjectService projectService,
                           CodeService codeService) {
        this.snapshotRepository = snapshotRepository;
        this.valueRepository = valueRepository;
        this.rawRepository = rawRepository;
        this.batchService = batchService;
        this.indicatorService = indicatorService;
        this.projectService = projectService;
        this.codeService = codeService;
    }

    @Transactional(readOnly = true)
    public PageResult<DataSnapshot> search(Long projectId, String status, String keyword, int page, int size) {
        size = PageLimits.size(size);
        // 数据权限：null = 不限制（管理员 / ALL 范围）；空集合 = 无可见项目，直接返回空页
        List<Long> ids = projectService.visibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<DataSnapshot> p = snapshotRepository.search(projectId, ids, blankToNull(status), blankToNull(keyword),
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DataSnapshot get(Long id) {
        DataSnapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("数据快照不存在"));
        // 数据权限：快照所属项目必须对当前账号可见（否则不能按 id 直读，包括其指标值）
        projectService.checkVisible(snapshot.getProjectId());
        return snapshot;
    }

    /** 数据权限守卫：按快照 id 取数据（指标值 / 概览 / 删除 / 标记已用）前先确认其所属项目可见。 */
    private void requireSnapshotVisible(Long snapshotId) {
        if (snapshotId == null) {
            return;
        }
        snapshotRepository.findById(snapshotId)
                .ifPresent(s -> projectService.checkVisible(s.getProjectId()));
    }

    @Transactional(readOnly = true)
    public List<SnapshotValue> values(Long snapshotId) {
        requireSnapshotVisible(snapshotId);
        return valueRepository.findBySnapshotIdOrderByObjectCodeAscIndicatorCodeAsc(snapshotId);
    }

    @Transactional(readOnly = true)
    public long valueCount(Long snapshotId) {
        requireSnapshotVisible(snapshotId);
        return valueRepository.countBySnapshotId(snapshotId);
    }

    /**
     * 由数据批次生成评估数据快照。
     */
    @Transactional
    public Map<String, Object> createFromBatch(Long batchId, Long versionId, String operator, String remark) {
        DataBatch batch = batchService.get(batchId);
        if ("FAILED".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.INPUT_VALIDATE_FAILED,
                    "该批次校验未通过（存在阻断级错误），请先修正数据后重新导入");
        }
        Long effectiveVersionId = versionId != null ? versionId : batch.getTargetVersionId();
        if (effectiveVersionId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成快照必须绑定指标体系版本");
        }
        IndicatorVersion version = indicatorService.get(effectiveVersionId);
        if (indicatorService.indicators(effectiveVersionId).isEmpty()) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "所选指标体系版本没有任何指标");
        }

        List<RawDataRecord> recs = rawRepository.findByBatchIdOrderByRowNoAsc(batchId);
        if (recs.isEmpty()) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "数据批次中没有记录，无法生成快照");
        }

        // 组装对象 -> 指标 -> 值（同一对象同一指标多条记录时保留最新时间点，并记录告警）
        Map<String, Map<String, RawDataRecord>> byObject = new LinkedHashMap<>();
        Map<String, String> objectNames = new LinkedHashMap<>();
        int duplicates = 0;
        for (RawDataRecord r : recs) {
            if (!Boolean.TRUE.equals(r.getValid()) || r.getObjectCode() == null || r.getIndicatorCode() == null) {
                continue;
            }
            Map<String, RawDataRecord> m = byObject.computeIfAbsent(r.getObjectCode(), k -> new LinkedHashMap<>());
            RawDataRecord exist = m.get(r.getIndicatorCode());
            if (exist == null) {
                m.put(r.getIndicatorCode(), r);
            } else {
                duplicates++;
                LocalDateTime t1 = exist.getDataTime();
                LocalDateTime t2 = r.getDataTime();
                if (t2 != null && (t1 == null || t2.isAfter(t1))) {
                    m.put(r.getIndicatorCode(), r);
                }
            }
            if (r.getObjectName() != null) {
                objectNames.put(r.getObjectCode(), r.getObjectName());
            }
        }
        if (byObject.isEmpty()) {
            throw new BizException(ErrorCode.INPUT_VALIDATE_FAILED, "批次中没有有效记录，无法生成快照");
        }

        // 读取指标体系（叶子指标参与评估）
        List<Indicator> allIndicators = indicatorService.indicators(effectiveVersionId);
        List<Indicator> leafIndicators = allIndicators.stream()
                .filter(i -> Boolean.TRUE.equals(i.getLeaf()))
                .sorted(Comparator.comparing(i -> i.getSortOrder() == null ? 0 : i.getSortOrder()))
                .toList();
        if (leafIndicators.isEmpty()) {
            leafIndicators = allIndicators;
        }

        // 构造算法输入以复用同一套标准化逻辑，保证快照与算法结果一致
        List<AlgorithmRequest.IndicatorMeta> metas = new ArrayList<>();
        for (Indicator i : leafIndicators) {
            AlgorithmRequest.IndicatorMeta m = new AlgorithmRequest.IndicatorMeta();
            m.setCode(i.getIndicatorCode());
            m.setName(i.getIndicatorName());
            m.setParentCode(i.getParentCode());
            m.setLevel(i.getLevel());
            m.setUnit(i.getUnit());
            m.setDirection(i.getDirection());
            m.setWeight(i.getWeight() == null ? null : i.getWeight().doubleValue());
            m.setMinValue(i.getMinValue() == null ? null : i.getMinValue().doubleValue());
            m.setMaxValue(i.getMaxValue() == null ? null : i.getMaxValue().doubleValue());
            m.setIntervalLower(i.getIntervalLower() == null ? null : i.getIntervalLower().doubleValue());
            m.setIntervalUpper(i.getIntervalUpper() == null ? null : i.getIntervalUpper().doubleValue());
            metas.add(m);
        }
        List<AlgorithmRequest.ObjectData> objects = new ArrayList<>();
        for (Map.Entry<String, Map<String, RawDataRecord>> e : byObject.entrySet()) {
            AlgorithmRequest.ObjectData o = new AlgorithmRequest.ObjectData();
            o.setObjectCode(e.getKey());
            o.setObjectName(objectNames.get(e.getKey()));
            for (Map.Entry<String, RawDataRecord> v : e.getValue().entrySet()) {
                if (v.getValue().getIndicatorValue() != null) {
                    o.getValues().put(v.getKey(), v.getValue().getIndicatorValue().doubleValue());
                }
            }
            objects.add(o);
        }
        Map<String, ScoringSupport.Range> ranges = ScoringSupport.effectiveRanges(metas, objects);
        Set<String> requiredCodes = new LinkedHashSet<>();
        for (Indicator i : leafIndicators) {
            requiredCodes.add(i.getIndicatorCode());
        }

        // 生成快照
        DataSnapshot snapshot = new DataSnapshot();
        snapshot.setSnapshotCode(codeService.next("SNAP", "data_snapshot", "snapshot_code"));
        snapshot.setProjectId(batch.getProjectId());
        snapshot.setProjectCode(batch.getProjectCode());
        snapshot.setIndicatorVersionId(effectiveVersionId);
        snapshot.setIndicatorVersionCode(version.getVersionCode());
        snapshot.setBatchId(batchId);
        snapshot.setBatchCode(batch.getBatchCode());
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setCreatedBy(operator);
        snapshot.setRemark(remark);
        snapshot.setStatus("GENERATED");
        snapshotRepository.save(snapshot);

        List<SnapshotValue> values = new ArrayList<>();
        int missing = 0;
        int outlier = 0;
        LocalDateTime minTime = null;
        LocalDateTime maxTime = null;
        StringBuilder checksumSource = new StringBuilder();

        for (AlgorithmRequest.ObjectData o : objects) {
            for (AlgorithmRequest.IndicatorMeta m : metas) {
                RawDataRecord raw = byObject.get(o.getObjectCode()).get(m.getCode());
                SnapshotValue sv = new SnapshotValue();
                sv.setSnapshotId(snapshot.getId());
                sv.setObjectCode(o.getObjectCode());
                sv.setObjectName(o.getObjectName());
                sv.setIndicatorCode(m.getCode());
                sv.setIndicatorName(m.getName());
                if (raw == null) {
                    sv.setMissing(true);
                    sv.setValid(false);
                    sv.setNormalized(false);
                    sv.setRemark("该对象缺少此指标数据");
                    missing++;
                } else {
                    sv.setDataTime(raw.getDataTime());
                    sv.setRawValue(raw.getIndicatorValue());
                    if (raw.getDataTime() != null) {
                        if (minTime == null || raw.getDataTime().isBefore(minTime)) {
                            minTime = raw.getDataTime();
                        }
                        if (maxTime == null || raw.getDataTime().isAfter(maxTime)) {
                            maxTime = raw.getDataTime();
                        }
                    }
                    ScoringSupport.Range r = ranges.get(m.getCode());
                    double normalized = MathSupport.normalize(raw.getIndicatorValue().doubleValue(),
                            m.getDirection(), r.min, r.max, m.getIntervalLower(), m.getIntervalUpper());
                    sv.setNormalizedValue(BigDecimal.valueOf(normalized));
                    sv.setNormalized(true);
                    boolean out = false;
                    if (m.getMinValue() != null && raw.getIndicatorValue().doubleValue() < m.getMinValue()) {
                        out = true;
                    }
                    if (m.getMaxValue() != null && raw.getIndicatorValue().doubleValue() > m.getMaxValue()) {
                        out = true;
                    }
                    if (out) {
                        sv.setOutlier(true);
                        sv.setRemark("值超出指标体系定义的上下界");
                        outlier++;
                    }
                    checksumSource.append(o.getObjectCode()).append('|').append(m.getCode())
                            .append('|').append(raw.getIndicatorValue().toPlainString()).append('\n');
                }
                values.add(sv);
            }
        }
        // 分批写入，避免单次过大
        int batchSize = 1000;
        for (int i = 0; i < values.size(); i += batchSize) {
            valueRepository.saveAll(values.subList(i, Math.min(i + batchSize, values.size())));
        }

        snapshot.setRecordCount(values.size());
        snapshot.setObjectCount(objects.size());
        snapshot.setIndicatorCount(metas.size());
        snapshot.setMissingCount(missing);
        snapshot.setOutlierCount(outlier);
        snapshot.setChecksum(sha256(checksumSource.toString()));
        snapshot.setDataRange(minTime == null ? null
                : minTime.toLocalDate() + " ~ " + maxTime.toLocalDate());
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("recordCount", values.size());
        quality.put("objectCount", objects.size());
        quality.put("indicatorCount", metas.size());
        quality.put("missingCount", missing);
        quality.put("outlierCount", outlier);
        quality.put("duplicateResolved", duplicates);
        quality.put("batchCode", batch.getBatchCode());
        quality.put("indicatorVersionCode", version.getVersionCode());
        quality.put("normalizationRanges", ranges);
        quality.put("generatedAt", LocalDateTime.now().toString());
        snapshot.setQualityReport(JsonUtils.toJson(quality));
        snapshotRepository.save(snapshot);

        // 批次标记为已生成快照
        batch.setStatus("SNAPSHOTTED");
        batchService.saveBatch(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", snapshot.getId());
        result.put("snapshotCode", snapshot.getSnapshotCode());
        result.put("recordCount", snapshot.getRecordCount());
        result.put("objectCount", snapshot.getObjectCount());
        result.put("indicatorCount", snapshot.getIndicatorCount());
        result.put("missingCount", missing);
        result.put("outlierCount", outlier);
        result.put("dataRange", snapshot.getDataRange());
        result.put("qualityReport", quality);
        log.info("生成数据快照 {} 对象={} 指标={} 记录={} 缺失={} 越界={}",
                snapshot.getSnapshotCode(), objects.size(), metas.size(), values.size(), missing, outlier);
        return result;
    }

    @Transactional
    public void delete(Long id) {
        requireSnapshotVisible(id);
        DataSnapshot s = get(id);
        if ("USED".equals(s.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该快照已被评估任务使用，为保证可追溯性不允许删除");
        }
        valueRepository.deleteBySnapshotId(id);
        snapshotRepository.delete(s);
    }

    @Transactional
    public void markUsed(Long id) {
        requireSnapshotVisible(id);
        DataSnapshot s = get(id);
        s.setStatus("USED");
        s.setUsedCount((s.getUsedCount() == null ? 0 : s.getUsedCount()) + 1);
        snapshotRepository.save(s);
    }

    /** 快照概览：对象列表、指标列表、数据矩阵（用于前端预览与图形）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long id, int maxObjects) {
        requireSnapshotVisible(id);
        DataSnapshot s = get(id);
        List<String> objects = valueRepository.findObjectCodes(id);
        List<String> indicators = valueRepository.findIndicatorCodes(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("snapshot", s);
        m.put("objects", objects);
        m.put("indicators", indicators);
        m.put("missingCount", valueRepository.countBySnapshotIdAndMissingTrue(id));
        m.put("outlierCount", valueRepository.countBySnapshotIdAndOutlierTrue(id));
        m.put("recordCount", valueRepository.countBySnapshotId(id));
        List<SnapshotValue> preview = new ArrayList<>();
        int limit = maxObjects <= 0 ? 20 : maxObjects;
        for (String o : objects) {
            if (preview.size() >= limit * Math.max(1, indicators.size())) {
                break;
            }
            preview.addAll(valueRepository.findBySnapshotIdAndObjectCodeOrderByIndicatorCodeAsc(id, o));
        }
        m.put("values", preview);
        return m;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
