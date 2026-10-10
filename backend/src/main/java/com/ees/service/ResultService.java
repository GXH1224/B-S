package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageResult;
import com.ees.domain.EvaluationResult;
import com.ees.domain.EvaluationTask;
import com.ees.domain.ResultDetail;
import com.ees.repository.EvaluationResultRepository;
import com.ees.repository.ResultDetailRepository;
import com.ees.repository.SnapshotValueRepository;
import com.ees.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评估结果服务（方案 4.6 / 7.1 / 7.2）。
 *
 * <p>文本、表格、图形三类展示使用同一结果数据集，图形数据全部来自后端保存的结果，
 * 不允许前端重新计算出与表格不一致的数值（方案 7.3 结果一致性）。</p>
 */
@Service
public class ResultService {

    private final EvaluationResultRepository resultRepository;
    private final ResultDetailRepository detailRepository;
    private final com.ees.repository.EvaluationTaskRepository taskRepository;
    private final SnapshotValueRepository snapshotValueRepository;
    private final ProjectService projectService;

    public ResultService(EvaluationResultRepository resultRepository, ResultDetailRepository detailRepository,
                         com.ees.repository.EvaluationTaskRepository taskRepository,
                         SnapshotValueRepository snapshotValueRepository,
                         ProjectService projectService) {
        this.resultRepository = resultRepository;
        this.detailRepository = detailRepository;
        this.taskRepository = taskRepository;
        this.snapshotValueRepository = snapshotValueRepository;
        this.projectService = projectService;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResult<EvaluationResult> search(Long projectId, Long taskId, String algorithm, String level,
                                               String publishStatus, String objectCode, int page, int size) {
        // 数据权限：null = 不限制（管理员 / ALL 范围）；空集合 = 无可见项目，直接返回空页
        List<Long> ids = projectService.visibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<EvaluationResult> p = resultRepository.search(projectId, ids, taskId, blankToNull(algorithm),
                blankToNull(level), blankToNull(publishStatus), blankToNull(objectCode),
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public EvaluationResult get(Long id) {
        EvaluationResult r = resultRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("评估结果不存在"));
        // 数据权限：结果所属项目必须对当前账号可见（否则不能按 id 直读）
        projectService.checkVisible(r.getProjectId());
        return r;
    }

    @Transactional(readOnly = true)
    public List<EvaluationResult> byTask(Long taskId) {
        requireTaskVisible(taskId);
        return resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId);
    }

    /**
     * 数据权限守卫：按 taskId 取数的接口（各图表、导出载荷、对比）统一先过这里。
     *
     * <p>任务不存在时不在这里报错，交给调用方原有的「查不到结果」逻辑处理，
     * 避免把「任务不存在」与「无权访问」混为一谈。</p>
     */
    private void requireTaskVisible(Long taskId) {
        if (taskId == null) {
            return;
        }
        EvaluationTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            projectService.checkVisible(task.getProjectId());
        }
    }

    /** 数据权限守卫：按 resultId 取明细前先确认该结果所属项目可见。 */
    private void requireResultVisible(Long resultId) {
        if (resultId == null) {
            return;
        }
        resultRepository.findById(resultId).ifPresent(r -> projectService.checkVisible(r.getProjectId()));
    }

    /** 结果明细表格（支持排序、筛选、分页）。 */
    @Transactional(readOnly = true)
    public PageResult<ResultDetail> details(Long resultId, String keyword, String sortBy, String order,
                                            int page, int size) {
        requireResultVisible(resultId);
        Sort sort = Sort.by("DESC".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC,
                allowedSortField(sortBy));
        Page<ResultDetail> p = detailRepository.searchByResult(resultId, blankToNull(keyword),
                PageRequest.of(Math.max(0, page - 1), size, sort));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    private String allowedSortField(String sortBy) {
        if (sortBy == null) {
            return "indicatorCode";
        }
        return switch (sortBy) {
            case "weight", "score", "contribution", "normalizedValue", "rawValue", "rankNo" -> sortBy;
            default -> "indicatorCode";
        };
    }

    /** 结果完整视图：文本结论 + 表格明细 + 图形数据 + 追溯信息。 */
    @Transactional(readOnly = true)
    public Map<String, Object> fullView(Long resultId, Long projectId) {
        EvaluationResult r = get(resultId);
        EvaluationTask task = taskRepository.findById(r.getTaskId()).orElse(null);
        List<ResultDetail> details = detailRepository.findByResultIdOrderByIndicatorCodeAsc(resultId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", r);
        m.put("details", details);
        m.put("parsedVector", parseDoubleList(r.getVector()));
        m.put("weights", JsonUtils.toMap(r.getWeights()));
        m.put("contributions", JsonUtils.toMap(r.getContributions()));
        m.put("warnings", JsonUtils.toStringList(r.getWarnings()));
        m.put("extra", JsonUtils.toMap(r.getExtra()));
        m.put("text", buildTextConclusion(r, details, task));
        m.put("traceability", buildTraceability(r, task));
        m.put("charts", buildAllCharts(r.getTaskId(), resultId, projectId));
        return m;
    }

    /** 文本结论（展示形式一：文本）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> textView(Long resultId) {
        EvaluationResult r = get(resultId);
        EvaluationTask task = taskRepository.findById(r.getTaskId()).orElse(null);
        List<ResultDetail> details = detailRepository.findByResultIdOrderByIndicatorCodeAsc(resultId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conclusion", buildTextConclusion(r, details, task));
        m.put("warnings", JsonUtils.toStringList(r.getWarnings()));
        m.put("improvements", buildImprovements(details));
        return m;
    }

    private Map<String, Object> buildTextConclusion(EvaluationResult r, List<ResultDetail> details,
                                                    EvaluationTask task) {
        Map<String, Object> m = new LinkedHashMap<>();
        BigDecimal score = r.getTotalScore();
        String level = r.getLevel() == null ? "未评级" : r.getLevel();
        StringBuilder sb = new StringBuilder();
        sb.append("被评估对象 ").append(r.getObjectCode());
        if (r.getObjectName() != null && !r.getObjectName().isBlank()) {
            sb.append("（").append(r.getObjectName()).append("）");
        }
        sb.append(" 采用 ").append(algorithmLabel(r.getAlgorithm())).append(" 评估");
        if (score != null) {
            sb.append("，综合得分 ").append(score.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        sb.append("，评判等级为“").append(level).append("”。");
        if (r.getRanking() != null) {
            sb.append("在本次任务的 ").append(resultRepository.countByTaskId(r.getTaskId()))
                    .append(" 个被评估对象中排名第 ").append(r.getRanking()).append(" 位。");
        }
        m.put("summary", sb.toString());

        if (details != null && !details.isEmpty()) {
            List<ResultDetail> sorted = new ArrayList<>(details);
            sorted.sort(Comparator.comparing(d -> d.getContribution() == null ? BigDecimal.ZERO : d.getContribution()));
            List<Map<String, Object>> weak = new ArrayList<>();
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                ResultDetail d = sorted.get(i);
                if (d.getContribution() == null || d.getContribution().doubleValue() <= 0) {
                    continue;
                }
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("indicatorCode", d.getIndicatorCode());
                w.put("indicatorName", d.getIndicatorName());
                w.put("score", d.getScore());
                w.put("weight", d.getWeight());
                w.put("contribution", d.getContribution());
                w.put("grade", d.getGrade());
                weak.add(w);
            }
            m.put("weakIndicators", weak);

            sorted.sort((a, b) -> {
                BigDecimal x = b.getContribution() == null ? BigDecimal.ZERO : b.getContribution();
                BigDecimal y = a.getContribution() == null ? BigDecimal.ZERO : a.getContribution();
                return x.compareTo(y);
            });
            List<Map<String, Object>> strong = new ArrayList<>();
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                ResultDetail d = sorted.get(i);
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("indicatorCode", d.getIndicatorCode());
                w.put("indicatorName", d.getIndicatorName());
                w.put("score", d.getScore());
                w.put("weight", d.getWeight());
                w.put("contribution", d.getContribution());
                w.put("grade", d.getGrade());
                strong.add(w);
            }
            m.put("strongIndicators", strong);
        }
        m.put("level", level);
        m.put("score", score);
        m.put("algorithm", r.getAlgorithm());
        m.put("algorithmLabel", algorithmLabel(r.getAlgorithm()));
        m.put("objectCode", r.getObjectCode());
        m.put("generatedAt", LocalDateTime.now().toString());
        return m;
    }

    private List<Map<String, Object>> buildImprovements(List<ResultDetail> details) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (details == null) {
            return out;
        }
        for (ResultDetail d : details) {
            double score = d.getScore() == null ? 0 : d.getScore().doubleValue();
            double weight = d.getWeight() == null ? 0 : d.getWeight().doubleValue();
            if (score < 80 && weight > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("indicatorCode", d.getIndicatorCode());
                m.put("indicatorName", d.getIndicatorName());
                m.put("score", d.getScore());
                m.put("weight", d.getWeight());
                m.put("suggestion", String.format(
                        "该指标得分 %.2f 低于 80 分且权重为 %.4f，提升该指标对综合得分影响最直接", score, weight));
                out.add(m);
            }
        }
        out.sort((a, b) -> Double.compare(
                ((BigDecimal) b.get("weight")).doubleValue() * (100 - ((BigDecimal) b.get("score")).doubleValue()),
                ((BigDecimal) a.get("weight")).doubleValue() * (100 - ((BigDecimal) a.get("score")).doubleValue())));
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    /** 追溯信息（方案 5.4 可追溯要求）。 */
    private Map<String, Object> buildTraceability(EvaluationResult r, EvaluationTask task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskCode", r.getTaskCode());
        m.put("taskName", task == null ? null : task.getTaskName());
        m.put("projectCode", r.getProjectCode());
        m.put("projectName", r.getProjectName());
        m.put("indicatorVersionCode", r.getIndicatorVersionCode());
        m.put("snapshotCode", r.getSnapshotCode());
        m.put("algorithm", r.getAlgorithm());
        m.put("algorithmVersion", r.getAlgorithmVersion());
        m.put("submitBy", task == null ? null : task.getSubmitBy());
        m.put("submittedAt", task == null ? null : task.getCreatedAt());
        m.put("startedAt", task == null ? null : task.getStartedAt());
        m.put("finishedAt", task == null ? null : task.getFinishedAt());
        m.put("durationMs", task == null ? null : task.getDurationMs());
        m.put("parameters", task == null ? Map.of() : JsonUtils.toMap(task.getParameters()));
        m.put("inputSummary", task == null ? Map.of() : JsonUtils.toMap(task.getInputSummary()));
        m.put("status", task == null ? null : task.getStatus());
        m.put("algorithmLabel", algorithmLabel(r.getAlgorithm()));
        return m;
    }

    // ------------------------------------------------------------------ 八类图形

    /** 一次返回全部图形数据，保证与表格同源。 */
    @Transactional(readOnly = true)
    public Map<String, Object> buildAllCharts(Long taskId, Long resultId, Long projectId) {
        requireTaskVisible(taskId);
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("bar", barChart(taskId, resultId));
        charts.put("line", lineChart(projectId, resultId));
        charts.put("pie", pieChart(resultId, taskId));
        charts.put("radar", radarChart(taskId));
        charts.put("scatter", scatterChart(taskId));
        charts.put("heatmap", heatmapChart(taskId));
        charts.put("boxplot", boxplotChart(taskId));
        charts.put("rankBar", rankBarChart(taskId));
        return charts;
    }

    /** 1. 柱状图：指标得分/贡献对比，支持排序。 */
    @Transactional(readOnly = true)
    public Map<String, Object> barChart(Long taskId, Long resultId) {
        requireTaskVisible(taskId);
        List<ResultDetail> details = detailsOf(taskId, resultId);
        details.sort((a, b) -> {
            BigDecimal x = b.getScore() == null ? BigDecimal.ZERO : b.getScore();
            BigDecimal y = a.getScore() == null ? BigDecimal.ZERO : a.getScore();
            return x.compareTo(y);
        });
        List<String> categories = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();
        List<BigDecimal> contributions = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (ResultDetail d : details) {
            categories.add(label(d));
            scores.add(round(d.getScore()));
            contributions.add(round(d.getContribution()));
            weights.add(round(d.getWeight()));
        }
        Map<String, Object> m = base("bar", "指标得分柱状图", "分", "同一评估结果的指标得分，按得分降序",
                taskId, resultId);
        m.put("categories", categories);
        m.put("series", List.of(
                series("得分", scores, "bar"),
                series("贡献值", contributions, "bar"),
                series("权重", weights, "bar")));
        m.put("sortable", true);
        return m;
    }

    /** 2. 折线图：多批次/多时间点趋势。 */
    @Transactional(readOnly = true)
    public Map<String, Object> lineChart(Long projectId, Long resultId) {
        if (projectId != null) {
            projectService.checkVisible(projectId);
        }
        EvaluationResult ref = resultId == null ? null : get(resultId);
        Long pid = projectId != null ? projectId : (ref == null ? null : ref.getProjectId());
        String objectCode = ref == null ? null : ref.getObjectCode();
        List<EvaluationResult> list = new ArrayList<>();
        if (pid != null) {
            for (EvaluationResult r : resultRepository.findByProjectIdOrderByIdAsc(pid)) {
                if (objectCode == null || objectCode.equals(r.getObjectCode())) {
                    list.add(r);
                }
            }
        }
        list.sort(Comparator.comparing(EvaluationResult::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<String> categories = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();
        Map<String, Object> m = base("line", "评估趋势折线图", "分", "同一项目/对象的多批次评估得分趋势",
                ref == null ? null : ref.getTaskId(), resultId);
        if (list.isEmpty()) {
            m.put("emptyReason", "暂无历史评估结果，无法生成趋势折线图。请至少完成两次评估任务。");
            m.put("categories", categories);
            m.put("series", List.of());
            return m;
        }
        for (EvaluationResult r : list) {
            categories.add(r.getTaskCode() == null ? String.valueOf(r.getId()) : r.getTaskCode());
            scores.add(round(r.getTotalScore()));
        }
        m.put("categories", categories);
        m.put("series", List.of(series(objectCode == null ? "综合得分" : objectCode + " 综合得分", scores, "line")));
        m.put("objectCode", objectCode);
        return m;
    }

    /** 3. 饼图：权重构成或等级分布。 */
    @Transactional(readOnly = true)
    public Map<String, Object> pieChart(Long resultId, Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("pie", "权重构成 / 等级分布饼图", null,
                "指标权重构成与评判等级分布，显示数值、比例和图例", taskId, resultId);
        List<Map<String, Object>> weightItems = new ArrayList<>();
        if (resultId != null) {
            for (ResultDetail d : detailRepository.findByResultIdOrderByIndicatorCodeAsc(resultId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", label(d));
                item.put("value", round(d.getWeight()));
                item.put("percent", d.getWeight() == null ? null
                        : d.getWeight().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
                weightItems.add(item);
            }
        }
        List<Map<String, Object>> levelItems = new ArrayList<>();
        if (taskId != null) {
            Map<String, Integer> counter = new LinkedHashMap<>();
            for (EvaluationResult r : resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId)) {
                counter.merge(r.getLevel() == null ? "未评级" : r.getLevel(), 1, Integer::sum);
            }
            counter.forEach((k, v) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", k);
                item.put("value", v);
                levelItems.add(item);
            });
        }
        m.put("weightPie", weightItems);
        m.put("levelPie", levelItems);
        m.put("series", List.of(weightItems));
        m.put("showPercent", true);
        return m;
    }

    /** 4. 雷达图：多维度能力对比，支持多对象叠加。 */
    @Transactional(readOnly = true)
    public Map<String, Object> radarChart(Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("radar", "多维度能力雷达图", "分",
                "同一任务下各被评估对象的指标得分对比，支持多对象叠加", taskId, null);
        if (taskId == null) {
            m.put("emptyReason", "未指定评估任务");
            return m;
        }
        List<ResultDetail> all = detailRepository.findByTaskIdOrderByIdAsc(taskId);
        if (all.isEmpty()) {
            m.put("emptyReason", "该任务没有指标明细数据");
            return m;
        }
        List<String> indicatorCodes = new ArrayList<>(new LinkedHashSet<>(
                all.stream().map(ResultDetail::getIndicatorCode).toList()));
        List<String> indicatorNames = new ArrayList<>();
        for (String code : indicatorCodes) {
            indicatorNames.add(all.stream().filter(d -> code.equals(d.getIndicatorCode()))
                    .map(ResultDetail::getIndicatorName).findFirst().orElse(code));
        }
        Map<String, List<BigDecimal>> byObject = new LinkedHashMap<>();
        for (ResultDetail d : all) {
            byObject.computeIfAbsent(d.getObjectCode() == null ? "未命名" : d.getObjectCode(),
                    k -> new ArrayList<>()).add(round(d.getScore()));
        }
        List<Map<String, Object>> series = new ArrayList<>();
        byObject.forEach((obj, values) -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", obj);
            s.put("value", values);
            s.put("type", "radar");
            series.add(s);
        });
        m.put("indicatorNames", indicatorNames);
        m.put("indicatorCodes", indicatorCodes);
        m.put("max", 100);
        m.put("series", series);
        return m;
    }

    /** 5. 散点图：两项指标关系，支持横纵轴选择。 */
    @Transactional(readOnly = true)
    public Map<String, Object> scatterChart(Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("scatter", "指标关系散点图", "分",
                "任意两项指标得分的相关性观察，数据点可查看明细", taskId, null);
        if (taskId == null) {
            m.put("emptyReason", "未指定评估任务");
            return m;
        }
        List<ResultDetail> all = detailRepository.findByTaskIdOrderByIdAsc(taskId);
        if (all.isEmpty()) {
            m.put("emptyReason", "该任务没有指标明细数据");
            return m;
        }
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(
                all.stream().map(ResultDetail::getIndicatorCode).toList()));
        if (codes.size() < 2) {
            m.put("emptyReason", "指标数量少于 2 项，无法生成散点图");
            return m;
        }
        String xCode = codes.get(0);
        String yCode = codes.get(1);
        Map<String, Map<String, BigDecimal>> byObject = new LinkedHashMap<>();
        for (ResultDetail d : all) {
            byObject.computeIfAbsent(d.getObjectCode() == null ? "未命名" : d.getObjectCode(),
                            k -> new LinkedHashMap<>())
                    .put(d.getIndicatorCode(), round(d.getScore()));
        }
        List<List<Object>> points = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> e : byObject.entrySet()) {
            BigDecimal x = e.getValue().get(xCode);
            BigDecimal y = e.getValue().get(yCode);
            if (x != null && y != null) {
                points.add(List.of(x, y, e.getKey()));
            }
        }
        m.put("xIndicatorCode", xCode);
        m.put("yIndicatorCode", yCode);
        m.put("xIndicatorName", nameOf(all, xCode));
        m.put("yIndicatorName", nameOf(all, yCode));
        m.put("availableIndicators", m3(codes, all));
        m.put("points", points);
        m.put("series", List.of(Map.of("name", "对象分布", "data", points, "type", "scatter")));
        return m;
    }

    private List<Map<String, Object>> m3(List<String> codes, List<ResultDetail> all) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String c : codes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", c);
            item.put("name", nameOf(all, c));
            out.add(item);
        }
        return out;
    }

    /** 6. 热力图：指标矩阵、批次得分或数据质量分布。 */
    @Transactional(readOnly = true)
    public Map<String, Object> heatmapChart(Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("heatmap", "指标-对象热力图", "分",
                "指标维度与对象维度的得分矩阵，支持颜色图例与单元格查看", taskId, null);
        if (taskId == null) {
            m.put("emptyReason", "未指定评估任务");
            return m;
        }
        List<ResultDetail> all = detailRepository.findByTaskIdOrderByIdAsc(taskId);
        if (all.isEmpty()) {
            m.put("emptyReason", "该任务没有指标明细数据");
            return m;
        }
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(
                all.stream().map(ResultDetail::getIndicatorCode).toList()));
        List<String> objects = new ArrayList<>(new LinkedHashSet<>(
                all.stream().map(d -> d.getObjectCode() == null ? "未命名" : d.getObjectCode()).toList()));
        List<Map<String, Object>> data = new ArrayList<>();
        double min = 100;
        double max = 0;
        for (int xi = 0; xi < codes.size(); xi++) {
            for (int yi = 0; yi < objects.size(); yi++) {
                final int fx = xi;
                final int fy = yi;
                BigDecimal v = all.stream()
                        .filter(d -> codes.get(fx).equals(d.getIndicatorCode())
                                && objects.get(fy).equals(d.getObjectCode() == null ? "未命名" : d.getObjectCode()))
                        .map(ResultDetail::getScore).filter(java.util.Objects::nonNull)
                        .findFirst().orElse(BigDecimal.ZERO);
                double dv = v.doubleValue();
                min = Math.min(min, dv);
                max = Math.max(max, dv);
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("value", List.of(xi, yi, round(v)));
                cell.put("indicatorCode", codes.get(xi));
                cell.put("objectCode", objects.get(yi));
                cell.put("indicatorName", nameOf(all, codes.get(xi)));
                data.add(cell);
            }
        }
        m.put("xLabels", codes);
        m.put("yLabels", objects);
        m.put("data", data);
        m.put("min", min);
        m.put("max", max);
        m.put("series", List.of(Map.of("name", "得分", "data", data, "type", "heatmap")));
        return m;
    }

    /** 7. 箱线图：多批次或多对象的分布、离群值和稳定性分析。 */
    @Transactional(readOnly = true)
    public Map<String, Object> boxplotChart(Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("boxplot", "指标得分分布箱线图", "分",
                "展示中位数、四分位与离群点，用于稳定性分析", taskId, null);
        if (taskId == null) {
            m.put("emptyReason", "未指定评估任务");
            return m;
        }
        List<ResultDetail> all = detailRepository.findByTaskIdOrderByIdAsc(taskId);
        if (all.isEmpty()) {
            m.put("emptyReason", "该任务没有指标明细数据");
            return m;
        }
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(
                all.stream().map(ResultDetail::getIndicatorCode).toList()));
        List<String> categories = new ArrayList<>();
        List<List<Double>> boxData = new ArrayList<>();
        List<List<Object>> outliers = new ArrayList<>();
        for (String code : codes) {
            List<Double> scores = new ArrayList<>();
            for (ResultDetail d : all) {
                if (code.equals(d.getIndicatorCode()) && d.getScore() != null) {
                    scores.add(d.getScore().doubleValue());
                }
            }
            if (scores.isEmpty()) {
                continue;
            }
            scores.sort(Double::compareTo);
            double min = scores.get(0);
            double max = scores.get(scores.size() - 1);
            double q1 = quantile(scores, 0.25);
            double median = quantile(scores, 0.5);
            double q3 = quantile(scores, 0.75);
            double iqr = q3 - q1;
            double lowerFence = q1 - 1.5 * iqr;
            double upperFence = q3 + 1.5 * iqr;
            List<Object> out = new ArrayList<>();
            for (double s : scores) {
                if (s < lowerFence || s > upperFence) {
                    out.add(s);
                }
            }
            categories.add(nameOf(all, code));
            boxData.add(List.of(min, q1, median, q3, max));
            outliers.add(out);
        }
        m.put("categories", categories);
        m.put("boxData", boxData);
        m.put("outliers", outliers);
        m.put("series", List.of(Map.of("name", "得分分布", "data", boxData, "type", "boxplot")));
        return m;
    }

    /** 8. 排名条形图：对象综合得分或指标贡献排序。 */
    @Transactional(readOnly = true)
    public Map<String, Object> rankBarChart(Long taskId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = base("rankBar", "对象综合得分排名条形图", "分",
                "支持正反序、前 N 项和并列名次", taskId, null);
        if (taskId == null) {
            m.put("emptyReason", "未指定评估任务");
            return m;
        }
        List<EvaluationResult> list = resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId);
        if (list.isEmpty()) {
            m.put("emptyReason", "该任务没有评估结果");
            return m;
        }
        List<String> categories = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();
        List<Integer> rankings = new ArrayList<>();
        List<String> levels = new ArrayList<>();
        for (EvaluationResult r : list) {
            categories.add(r.getObjectCode() + (r.getObjectName() == null ? "" : " " + r.getObjectName()));
            scores.add(round(r.getTotalScore()));
            rankings.add(r.getRanking());
            levels.add(r.getLevel());
        }
        m.put("categories", categories);
        m.put("rankings", rankings);
        m.put("levels", levels);
        m.put("series", List.of(Map.of("name", "综合得分", "data", scores, "type", "bar", "rankings", rankings)));
        return m;
    }

    // ------------------------------------------------------------------ 复核与发布

    @Transactional
    public EvaluationResult review(Long resultId, String action, String comment, String operator) {
        EvaluationResult r = get(resultId);
        switch (action == null ? "" : action.toUpperCase()) {
            case "APPROVE" -> {
                r.setReviewed("APPROVED");
                r.setReviewComment(comment);
                r.setReviewedBy(operator);
                r.setReviewedAt(LocalDateTime.now());
            }
            case "REJECT" -> {
                r.setReviewed("REJECTED");
                r.setReviewComment(comment);
                r.setReviewedBy(operator);
                r.setReviewedAt(LocalDateTime.now());
                r.setPublishStatus("DRAFT");
            }
            case "SUBMIT" -> {
                r.setReviewed("PENDING");
                r.setReviewComment(comment);
                r.setReviewedBy(operator);
                r.setReviewedAt(LocalDateTime.now());
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的复核动作：" + action + "，应为 SUBMIT / APPROVE / REJECT");
        }
        // 同步任务状态
        taskRepository.findById(r.getTaskId()).ifPresent(t -> {
            if ("SUCCESS".equals(t.getStatus()) && "PENDING".equals(r.getReviewed())) {
                t.setStatus("PENDING_REVIEW");
                taskRepository.save(t);
            }
        });
        return resultRepository.save(r);
    }

    @Transactional
    public EvaluationResult publish(Long resultId, String operator) {
        EvaluationResult r = get(resultId);
        if ("REJECTED".equals(r.getReviewed())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "已被退回的结果不允许发布，请重新执行评估");
        }
        r.setPublishStatus("PUBLISHED");
        r.setPublishedAt(LocalDateTime.now());
        r.setPublishedBy(operator);
        resultRepository.save(r);
        taskRepository.findById(r.getTaskId()).ifPresent(t -> {
            t.setStatus("PUBLISHED");
            taskRepository.save(t);
        });
        return r;
    }

    @Transactional
    public EvaluationResult archive(Long resultId, String operator) {
        EvaluationResult r = get(resultId);
        r.setPublishStatus("ARCHIVED");
        resultRepository.save(r);
        return r;
    }

    /** 同项目多对象/多任务横向对比。 */
    @Transactional(readOnly = true)
    public Map<String, Object> compare(Long projectId, Long taskId) {
        if (projectId != null) {
            projectService.checkVisible(projectId);
        }
        requireTaskVisible(taskId);
        List<EvaluationResult> list = taskId != null
                ? resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId)
                : resultRepository.findTop10ByProjectIdOrderByIdDesc(projectId);
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> indicatorCodes = new LinkedHashSet<>();
        Map<Long, List<ResultDetail>> detailMap = new LinkedHashMap<>();
        for (EvaluationResult r : list) {
            List<ResultDetail> details = detailRepository.findByResultIdOrderByIndicatorCodeAsc(r.getId());
            detailMap.put(r.getId(), details);
            details.forEach(d -> indicatorCodes.add(d.getIndicatorCode()));
        }
        for (EvaluationResult r : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resultId", r.getId());
            row.put("taskCode", r.getTaskCode());
            row.put("objectCode", r.getObjectCode());
            row.put("objectName", r.getObjectName());
            row.put("totalScore", r.getTotalScore());
            row.put("level", r.getLevel());
            row.put("ranking", r.getRanking());
            row.put("algorithm", r.getAlgorithm());
            Map<String, BigDecimal> scores = new LinkedHashMap<>();
            for (ResultDetail d : detailMap.get(r.getId())) {
                scores.put(d.getIndicatorCode(), d.getScore());
            }
            row.put("indicatorScores", scores);
            rows.add(row);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("indicatorCodes", new ArrayList<>(indicatorCodes));
        m.put("rows", rows);
        m.put("count", rows.size());
        return m;
    }

    /** 导出数据装配（供 ReportService 使用）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> exportPayload(Long taskId, Long resultId) {
        requireTaskVisible(taskId);
        Map<String, Object> m = new LinkedHashMap<>();
        EvaluationTask task = taskRepository.findById(taskId).orElseThrow(
                () -> BizException.notFound("评估任务不存在"));
        m.put("task", task);
        m.put("project", projectService.get(task.getProjectId()));
        m.put("results", resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId));
        List<Map<String, Object>> detailRows = new ArrayList<>();
        for (EvaluationResult r : resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId)) {
            for (ResultDetail d : detailRepository.findByResultIdOrderByIndicatorCodeAsc(r.getId())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("objectCode", r.getObjectCode());
                row.put("objectName", r.getObjectName());
                row.put("totalScore", r.getTotalScore());
                row.put("level", r.getLevel());
                row.put("ranking", r.getRanking());
                row.put("indicatorCode", d.getIndicatorCode());
                row.put("indicatorName", d.getIndicatorName());
                row.put("unit", d.getUnit());
                row.put("direction", d.getDirection());
                row.put("rawValue", d.getRawValue());
                row.put("normalizedValue", d.getNormalizedValue());
                row.put("weight", d.getWeight());
                row.put("contribution", d.getContribution());
                row.put("score", d.getScore());
                row.put("grade", d.getGrade());
                detailRows.add(row);
            }
        }
        m.put("detailRows", detailRows);
        m.put("charts", buildAllCharts(taskId, resultId, task.getProjectId()));
        m.put("generatedAt", LocalDateTime.now());
        return m;
    }

    // ------------------------------------------------------------------ 辅助

    private List<ResultDetail> detailsOf(Long taskId, Long resultId) {
        if (resultId != null) {
            return new ArrayList<>(detailRepository.findByResultIdOrderByIndicatorCodeAsc(resultId));
        }
        if (taskId != null) {
            List<EvaluationResult> list = resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId);
            if (!list.isEmpty()) {
                return new ArrayList<>(detailRepository.findByResultIdOrderByIndicatorCodeAsc(list.get(0).getId()));
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "请提供 resultId 或 taskId");
    }

    private Map<String, Object> base(String type, String title, String unit, String description,
                                     Long taskId, Long resultId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chartType", type);
        m.put("title", title);
        m.put("unit", unit);
        m.put("description", description);
        m.put("taskId", taskId);
        m.put("resultId", resultId);
        m.put("generatedAt", LocalDateTime.now().toString());
        m.put("dataSource", "后端评估结果数据集（与表格同源）");
        return m;
    }

    private Map<String, Object> series(String name, List<BigDecimal> data, String type) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("type", type);
        s.put("data", data);
        return s;
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP);
    }

    private static String label(ResultDetail d) {
        return d.getIndicatorName() == null ? d.getIndicatorCode()
                : d.getIndicatorName() + "(" + d.getIndicatorCode() + ")";
    }

    private static String nameOf(List<ResultDetail> all, String code) {
        return all.stream().filter(d -> code.equals(d.getIndicatorCode()))
                .map(ResultDetail::getIndicatorName).filter(java.util.Objects::nonNull)
                .findFirst().orElse(code);
    }

    private static double quantile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double pos = q * (sorted.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double frac = pos - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    private static List<Double> parseDoubleList(String json) {
        List<Double> out = new ArrayList<>();
        List<Object> raw = JsonUtils.parseQuietly(json, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
        }, List.of());
        for (Object o : raw) {
            if (o instanceof Number n) {
                out.add(n.doubleValue());
            }
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String algorithmLabel(String code) {
        if (code == null) {
            return "未知算法";
        }
        return switch (code.toUpperCase()) {
            case "AHP" -> "层次分析法（AHP）";
            case "FUZZY" -> "模糊综合评判";
            case "WEIGHTED" -> "加权平均法";
            case "COMBO" -> "组合评估流程";
            default -> code;
        };
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
