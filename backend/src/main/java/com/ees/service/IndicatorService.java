package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageLimits;
import com.ees.common.PageResult;
import com.ees.domain.Indicator;
import com.ees.domain.IndicatorImportBatch;
import com.ees.domain.IndicatorVersion;
import com.ees.repository.IndicatorImportBatchRepository;
import com.ees.repository.IndicatorRepository;
import com.ees.repository.IndicatorVersionRepository;
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
 * 指标体系接入服务（方案 4.3 / 2.2）。
 *
 * <p>系统只负责导入、校验、版本化和使用，不负责重新设计指标体系。
 * 导入后的原始版本只读保存；生成不可变版本号，保留导入文件、导入时间、导入人和校验结果。</p>
 */
@Service
public class IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    /** 模板列定义（与 samples/指标模板 一致） */
    public static final List<String> TEMPLATE_COLUMNS = List.of(
            "indicator_code", "indicator_name", "parent_code", "level", "unit", "direction",
            "weight", "min_value", "max_value", "interval_lower", "interval_upper",
            "optimal_value", "score_rule", "sort_order", "remark");

    private final IndicatorVersionRepository versionRepository;
    private final IndicatorRepository indicatorRepository;
    private final IndicatorImportBatchRepository importBatchRepository;
    private final ProjectService projectService;

    public IndicatorService(IndicatorVersionRepository versionRepository,
                            IndicatorRepository indicatorRepository,
                            IndicatorImportBatchRepository importBatchRepository,
                            ProjectService projectService) {
        this.versionRepository = versionRepository;
        this.indicatorRepository = indicatorRepository;
        this.importBatchRepository = importBatchRepository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public PageResult<IndicatorVersion> search(String keyword, String status, Long projectId, int page, int size) {
        size = PageLimits.size(size);
        // 数据权限：null = 不限制（管理员 / ALL 范围）；空集合 = 无可见项目，
        // 此时仍应看到公共版本（projectId 为空），因此不能直接返回空页，交给查询里的
        // 「:ids is null or v.projectId is null or v.projectId in :ids」处理。
        List<Long> ids = projectService.visibleProjectIds();
        Page<IndicatorVersion> p = versionRepository.search(
                blankToNull(keyword), blankToNull(status), projectId, ids,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public IndicatorVersion get(Long id) {
        IndicatorVersion version = versionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BizException.notFound("指标体系版本不存在"));
        // 数据权限：公共版本（projectId 为空）对所有项目可见，其余必须属于当前账号可见的项目
        if (version.getProjectId() != null) {
            projectService.checkVisible(version.getProjectId());
        }
        return version;
    }

    /** 数据权限守卫：公共版本（projectId 为空）对所有项目可见，其余必须属于当前账号可见的项目。 */
    private void requireVersionVisible(Long versionId) {
        if (versionId == null) {
            return;
        }
        versionRepository.findByIdAndDeletedFalse(versionId).ifPresent(v -> {
            if (v.getProjectId() != null) {
                projectService.checkVisible(v.getProjectId());
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Indicator> indicators(Long versionId) {
        requireVersionVisible(versionId);
        return indicatorRepository.findByVersionIdOrderBySortOrderAscIdAsc(versionId);
    }

    /** 构造指标树，供前端 el-tree / 树形表格使用。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> indicatorTree(Long versionId) {
        requireVersionVisible(versionId);
        List<Indicator> all = indicators(versionId);
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Indicator i : all) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", i.getId());
            n.put("code", i.getIndicatorCode());
            n.put("name", i.getIndicatorName());
            n.put("parentCode", i.getParentCode());
            n.put("level", i.getLevel());
            n.put("unit", i.getUnit());
            n.put("direction", i.getDirection());
            n.put("weight", i.getWeight());
            n.put("leaf", i.getLeaf());
            n.put("minValue", i.getMinValue());
            n.put("maxValue", i.getMaxValue());
            n.put("intervalLower", i.getIntervalLower());
            n.put("intervalUpper", i.getIntervalUpper());
            n.put("sortOrder", i.getSortOrder());
            n.put("remark", i.getRemark());
            n.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(i.getIndicatorCode(), n);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Indicator i : all) {
            Map<String, Object> n = nodes.get(i.getIndicatorCode());
            String parent = i.getParentCode();
            if (parent == null || parent.isBlank() || !nodes.containsKey(parent)) {
                roots.add(n);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodes.get(parent).get("children");
                children.add(n);
            }
        }
        return roots;
    }

    /**
     * 导入指标体系文件（Excel / CSV）。返回版本与校验结果。
     */
    @Transactional
    public Map<String, Object> importFromFile(MultipartFile file, Long projectId, String versionCode,
                                             String versionName, String description, String operator) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要导入的指标体系文件");
        }
        // 数据权限：只能往自己可见的项目里导入指标体系（留空 = 公共版本，允许）
        if (projectId != null) {
            projectService.checkVisible(projectId);
        }
        String fileName = file.getOriginalFilename();
        TabularReader.TableData data;
        String hash;
        try (InputStream in = file.getInputStream()) {
            hash = sha256(in);
        } catch (Exception e) {
            hash = null;
        }
        try (InputStream in = file.getInputStream()) {
            data = TabularReader.read(in, fileName);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "文件读取失败：" + e.getMessage(), e);
        }

        if (data.size() == 0) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "文件中没有数据行");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Indicator> parsed = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();

        int rowNo = 0;
        for (int idx = 0; idx < data.size(); idx++) {
            Map<String, String> row = data.getRows().get(idx);
            int lineNo = data.getRowNumbers().get(idx);
            rowNo++;
            String code = data.get(row, "indicator_code", "指标编码", "code");
            String name = data.get(row, "indicator_name", "指标名称", "name");
            if (code == null || code.isBlank()) {
                errors.add(err(lineNo, "indicator_code", "指标编码不能为空"));
                continue;
            }
            if (name == null || name.isBlank()) {
                errors.add(err(lineNo, "indicator_name", "指标名称不能为空"));
                continue;
            }
            if (codes.contains(code)) {
                errors.add(err(lineNo, "indicator_code", "指标编码重复：" + code));
                continue;
            }
            codes.add(code);

            Indicator ind = new Indicator();
            ind.setIndicatorCode(code);
            ind.setIndicatorName(name);
            ind.setParentCode(data.get(row, "parent_code", "上级指标编码", "parent"));
            ind.setUnit(data.get(row, "unit", "单位"));
            ind.setRemark(data.get(row, "remark", "备注"));

            String dirRaw = data.get(row, "direction", "方向", "指标方向");
            String dir = normalizeDirection(dirRaw);
            if (dir == null) {
                errors.add(err(lineNo, "direction", "指标方向非法：" + dirRaw + "，应为 POSITIVE / NEGATIVE / INTERVAL"));
                continue;
            }
            ind.setDirection(dir);

            Integer level = parseInt(data.get(row, "level", "层级"));
            ind.setLevel(level == null ? 1 : level);
            Integer sortOrder = parseInt(data.get(row, "sort_order", "排序"));
            ind.setSortOrder(sortOrder == null ? rowNo : sortOrder);

            Double weight = TabularReader.parseNumber(data.get(row, "weight", "权重"));
            if (weight != null) {
                if (weight < 0 || weight > 1) {
                    warnings.add(warn(lineNo, "weight", "权重 " + weight + " 不在 [0,1] 区间，将按同层归一化处理"));
                } else if (weight == 0) {
                    warnings.add(warn(lineNo, "weight", "权重为 0，该指标不会影响评估结果"));
                }
                ind.setWeight(BigDecimal.valueOf(weight));
            } else {
                warnings.add(warn(lineNo, "weight", "未提供权重，评估时将按同层等权处理"));
            }

            ind.setMinValue(decimal(data.get(row, "min_value", "最小值")));
            ind.setMaxValue(decimal(data.get(row, "max_value", "最大值")));
            ind.setIntervalLower(decimal(data.get(row, "interval_lower", "区间下界")));
            ind.setIntervalUpper(decimal(data.get(row, "interval_upper", "区间上界")));
            ind.setOptimalValue(decimal(data.get(row, "optimal_value", "最优值")));

            String rule = data.get(row, "score_rule", "评分规则");
            if (rule != null) {
                if (!rule.trim().startsWith("{") && !rule.trim().startsWith("[")) {
                    warnings.add(warn(lineNo, "score_rule", "评分规则不是合法 JSON，已按文本保存"));
                }
                ind.setScoreRule(rule);
            }
            if (ind.getMinValue() != null && ind.getMaxValue() != null
                    && ind.getMinValue().compareTo(ind.getMaxValue()) >= 0) {
                errors.add(err(lineNo, "min_value", "最小值必须小于最大值"));
                continue;
            }
            if ("INTERVAL".equals(dir) && (ind.getIntervalLower() == null || ind.getIntervalUpper() == null)) {
                warnings.add(warn(lineNo, "interval_lower", "区间型指标未提供区间上下界，标准化时将退化为正向指标"));
            }
            parsed.add(ind);
        }

        // 层级关系校验
        Set<String> allCodes = new LinkedHashSet<>();
        for (Indicator i : parsed) {
            allCodes.add(i.getIndicatorCode());
        }
        for (Indicator i : parsed) {
            String p = i.getParentCode();
            if (p != null && !p.isBlank() && !allCodes.contains(p)) {
                errors.add(err(0, "parent_code", "指标 " + i.getIndicatorCode() + " 的上级指标 " + p + " 不存在"));
            }
        }
        // 循环引用检测
        Map<String, String> parentMap = new HashMap<>();
        for (Indicator i : parsed) {
            parentMap.put(i.getIndicatorCode(), i.getParentCode());
        }
        for (String c : allCodes) {
            Set<String> visited = new LinkedHashSet<>();
            String cur = c;
            while (cur != null && !cur.isBlank()) {
                if (!visited.add(cur)) {
                    errors.add(err(0, "parent_code", "指标层级存在循环引用：" + c));
                    break;
                }
                cur = parentMap.get(cur);
            }
        }
        // 方向缺失检查
        long directionMissing = parsed.stream().filter(i -> i.getDirection() == null).count();
        if (directionMissing > 0) {
            warnings.add(warn(0, "direction", "存在 " + directionMissing + " 条指标未确认正负方向，未确认方向不得执行需要标准化的算法"));
        }

        boolean hasError = !errors.isEmpty();
        if (hasError && parsed.isEmpty()) {
            // 全部失败：不创建版本，直接返回错误明细
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("versionId", null);
            result.put("versionCode", null);
            result.put("totalRows", data.size());
            result.put("successRows", 0);
            result.put("errorRows", errors.size());
            result.put("errors", errors);
            result.put("warnings", warnings);
            return result;
        }

        // 生成版本
        String vCode = versionCode != null && !versionCode.isBlank()
                ? versionCode.trim() : nextVersionCode();
        if (versionRepository.existsByVersionCode(vCode)) {
            throw new BizException(ErrorCode.DATA_CONFLICT, "指标体系版本编号已存在：" + vCode);
        }
        IndicatorVersion version = new IndicatorVersion();
        version.setVersionCode(vCode);
        version.setVersionName(versionName != null && !versionName.isBlank() ? versionName
                : (fileName == null ? vCode : fileName));
        version.setProjectId(projectId);
        version.setSourceType("FILE");
        version.setSourceFile(fileName);
        version.setSourceHash(hash);
        version.setStatus(hasError ? "DRAFT" : "VALIDATED");
        version.setDescription(description);
        version.setCreatedBy(operator);
        version.setUpdatedBy(operator);
        versionRepository.save(version);

        // 计算层级路径与叶子标记
        Map<String, Indicator> byCode = new LinkedHashMap<>();
        for (Indicator i : parsed) {
            byCode.put(i.getIndicatorCode(), i);
        }
        int maxLevel = 0;
        for (Indicator i : parsed) {
            StringBuilder path = new StringBuilder();
            List<String> chain = new ArrayList<>();
            String cur = i.getIndicatorCode();
            int guard = 0;
            while (cur != null && !cur.isBlank() && guard++ < 100) {
                chain.add(0, cur);
                String p = parentMap.get(cur);
                cur = p;
            }
            for (String c : chain) {
                path.append("/").append(c);
            }
            i.setLevelPath(path.toString());
            i.setLevel(chain.size());
            maxLevel = Math.max(maxLevel, chain.size());
            i.setLeaf(true);
            i.setVersionId(version.getId());
        }
        for (Indicator i : parsed) {
            String p = i.getParentCode();
            if (p != null && !p.isBlank() && byCode.containsKey(p)) {
                byCode.get(p).setLeaf(false);
            }
        }
        // 权重归一化提示（同层）
        Map<String, Double> levelWeightSum = new LinkedHashMap<>();
        for (Indicator i : parsed) {
            String key = i.getLevelPath() == null ? "root" : parentKey(i.getLevelPath());
            levelWeightSum.merge(key, i.getWeight() == null ? 0d : i.getWeight().doubleValue(), Double::sum);
        }
        for (Map.Entry<String, Double> e : levelWeightSum.entrySet()) {
            if (e.getValue() > 0 && Math.abs(e.getValue() - 1.0) > 0.001) {
                warnings.add(warn(0, "weight", String.format(
                        "层级 %s 下权重之和为 %.4f（不等于 1），评估时系统将按配置归一化并记录归一化前后权重",
                        e.getKey().isEmpty() ? "根" : e.getKey(), e.getValue())));
            }
        }

        indicatorRepository.saveAll(parsed);

        Map<String, Object> validateResult = new LinkedHashMap<>();
        validateResult.put("totalRows", data.size());
        validateResult.put("successRows", parsed.size());
        validateResult.put("errorRows", errors.size());
        validateResult.put("errors", errors);
        validateResult.put("warnings", warnings);
        validateResult.put("importedAt", LocalDateTime.now().toString());

        version.setIndicatorCount(parsed.size());
        version.setLeafCount((int) parsed.stream().filter(Indicator::getLeaf).count());
        version.setMaxLevel(maxLevel);
        version.setValidateResult(JsonUtils.toJson(validateResult));
        versionRepository.save(version);

        IndicatorImportBatch batch = new IndicatorImportBatch();
        batch.setVersionId(version.getId());
        batch.setFileName(fileName);
        batch.setFileSize(file.getSize());
        batch.setFileHash(hash);
        batch.setTotalRows(data.size());
        batch.setSuccessRows(parsed.size());
        batch.setErrorRows(errors.size());
        batch.setStatus(errors.isEmpty() ? "SUCCESS" : "PARTIAL");
        batch.setErrorDetail(JsonUtils.toJson(errors));
        batch.setCreatedBy(operator);
        importBatchRepository.save(batch);

        version.setImportBatchId(batch.getId());
        versionRepository.save(version);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", errors.isEmpty());
        result.put("versionId", version.getId());
        result.put("versionCode", version.getVersionCode());
        result.put("versionName", version.getVersionName());
        result.put("status", version.getStatus());
        result.put("totalRows", data.size());
        result.put("successRows", parsed.size());
        result.put("errorRows", errors.size());
        result.put("indicatorCount", version.getIndicatorCount());
        result.put("leafCount", version.getLeafCount());
        result.put("maxLevel", version.getMaxLevel());
        result.put("errors", errors);
        result.put("warnings", warnings);
        log.info("指标体系导入完成 version={} 指标数={} 错误={}", vCode, parsed.size(), errors.size());
        return result;
    }

    /** 重新校验已导入版本（不修改原始指标数据）。 */
    @Transactional
    public Map<String, Object> revalidate(Long versionId) {
        IndicatorVersion version = get(versionId);
        List<Indicator> list = indicators(versionId);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (Indicator i : list) {
            if (!codes.add(i.getIndicatorCode())) {
                errors.add(err(0, "indicator_code", "指标编码重复：" + i.getIndicatorCode()));
            }
            if (i.getWeight() == null) {
                warnings.add(warn(0, "weight", "指标 " + i.getIndicatorCode() + " 未定义权重"));
            }
            if (i.getDirection() == null || i.getDirection().isBlank()) {
                errors.add(err(0, "direction", "指标 " + i.getIndicatorCode() + " 未确认正负方向"));
            }
        }
        Map<String, Object> vr = new LinkedHashMap<>();
        vr.put("totalRows", list.size());
        vr.put("successRows", list.size() - errors.size());
        vr.put("errorRows", errors.size());
        vr.put("errors", errors);
        vr.put("warnings", warnings);
        vr.put("validatedAt", LocalDateTime.now().toString());
        version.setValidateResult(JsonUtils.toJson(vr));
        version.setStatus(errors.isEmpty() ? "VALIDATED" : "INVALID");
        versionRepository.save(version);
        return vr;
    }

    @Transactional
    public IndicatorVersion publish(Long versionId, String operator) {
        IndicatorVersion v = get(versionId);
        if (indicatorRepository.countByVersionId(versionId) == 0) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该版本没有任何指标，无法发布");
        }
        v.setStatus("PUBLISHED");
        v.setPublishedAt(LocalDateTime.now());
        v.setPublishedBy(operator);
        v.setUpdatedBy(operator);
        return versionRepository.save(v);
    }

    @Transactional
    public void archive(Long versionId, String operator) {
        IndicatorVersion v = get(versionId);
        v.setStatus("ARCHIVED");
        v.setUpdatedBy(operator);
        versionRepository.save(v);
    }

    @Transactional
    public void delete(Long versionId) {
        IndicatorVersion v = get(versionId);
        if ("PUBLISHED".equals(v.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "已发布的指标体系版本不允许删除，请先归档");
        }
        indicatorRepository.deleteByVersionId(versionId);
        v.setDeleted(true);
        versionRepository.save(v);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> importBatches(Long versionId) {
        requireVersionVisible(versionId);
        List<IndicatorImportBatch> list = versionId == null
                ? importBatchRepository.findTop100ByOrderByIdDesc()
                : importBatchRepository.findByVersionIdOrderByIdDesc(versionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (IndicatorImportBatch b : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("versionId", b.getVersionId());
            m.put("fileName", b.getFileName());
            m.put("fileSize", b.getFileSize());
            m.put("totalRows", b.getTotalRows());
            m.put("successRows", b.getSuccessRows());
            m.put("errorRows", b.getErrorRows());
            m.put("status", b.getStatus());
            m.put("errorDetail", JsonUtils.toMapList(b.getErrorDetail()));
            m.put("createdBy", b.getCreatedBy());
            m.put("createdAt", b.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    /** 供数据校验使用：版本下的指标编码 -> 指标。 */
    @Transactional(readOnly = true)
    public Map<String, Indicator> codeIndex(Long versionId) {
        Map<String, Indicator> map = new LinkedHashMap<>();
        for (Indicator i : indicators(versionId)) {
            map.put(i.getIndicatorCode(), i);
        }
        return map;
    }

    public synchronized String nextVersionCode() {
        String year = String.valueOf(LocalDateTime.now().getYear());
        int seq = 1;
        String code;
        do {
            code = String.format("IND-%s-%03d", year, seq++);
        } while (versionRepository.existsByVersionCode(code) && seq < 10000);
        return code;
    }

    private static String parentKey(String levelPath) {
        int idx = levelPath.lastIndexOf('/');
        return idx <= 0 ? "" : levelPath.substring(0, idx);
    }

    private static String normalizeDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return "POSITIVE";
        }
        String s = raw.trim().toUpperCase();
        switch (s) {
            case "POSITIVE", "P", "正向", "正", "+":
                return "POSITIVE";
            case "NEGATIVE", "N", "负向", "负", "-":
                return "NEGATIVE";
            case "INTERVAL", "区间", "区间型":
                return "INTERVAL";
            default:
                return null;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String s) {
        Double d = TabularReader.parseNumber(s);
        return d == null ? null : BigDecimal.valueOf(d);
    }

    private static Map<String, Object> err(int row, String field, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("field", field);
        m.put("message", message);
        m.put("severity", "ERROR");
        return m;
    }

    private static Map<String, Object> warn(int row, String field, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("field", field);
        m.put("message", message);
        m.put("severity", "WARN");
        return m;
    }

    private static String sha256(InputStream in) {
        try {
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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
