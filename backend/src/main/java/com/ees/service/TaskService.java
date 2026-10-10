package com.ees.service;

import com.ees.algorithm.AlgorithmEngine;
import com.ees.algorithm.AlgorithmRegistry;
import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.AlgorithmResult;
import com.ees.algorithm.AlgorithmType;
import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageResult;
import com.ees.config.EesProperties;
import com.ees.domain.AhpConsistency;
import com.ees.domain.DataSnapshot;
import com.ees.domain.EvaluationResult;
import com.ees.domain.EvaluationTask;
import com.ees.domain.Indicator;
import com.ees.domain.IndicatorVersion;
import com.ees.domain.ResultDetail;
import com.ees.domain.SnapshotValue;
import com.ees.domain.TaskStatusHistory;
import com.ees.repository.AhpConsistencyRepository;
import com.ees.repository.EvaluationResultRepository;
import com.ees.repository.EvaluationTaskRepository;
import com.ees.repository.ResultDetailRepository;
import com.ees.repository.SnapshotValueRepository;
import com.ees.repository.TaskStatusHistoryRepository;
import com.ees.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评估任务服务（方案 4.5 / 8.3）。
 *
 * <p>任务状态机：草稿 → 校验中 → 已排队 → 运行中 → 成功、失败或已停止 →
 * 待审核 → 已发布 → 已归档。状态变化记录操作者、时间、原因和前后状态；
 * 失败任务可在保留原记录的前提下重试生成新执行实例。</p>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /** 任务取消标记（运行中任务可被请求停止） */
    private final Map<Long, Boolean> cancelFlags = new ConcurrentHashMap<>();

    private final EvaluationTaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final EvaluationResultRepository resultRepository;
    private final ResultDetailRepository detailRepository;
    private final AhpConsistencyRepository ahpRepository;
    private final SnapshotValueRepository snapshotValueRepository;
    private final AlgorithmRegistry algorithmRegistry;
    private final SnapshotService snapshotService;
    private final IndicatorService indicatorService;
    private final ProjectService projectService;
    private final ConfigService configService;
    private final CodeService codeService;
    private final EesProperties properties;

    public TaskService(EvaluationTaskRepository taskRepository, TaskStatusHistoryRepository historyRepository,
                       EvaluationResultRepository resultRepository, ResultDetailRepository detailRepository,
                       AhpConsistencyRepository ahpRepository,
                       SnapshotValueRepository snapshotValueRepository,
                       AlgorithmRegistry algorithmRegistry, SnapshotService snapshotService,
                       IndicatorService indicatorService, ProjectService projectService,
                       ConfigService configService,
                       CodeService codeService,
                       EesProperties properties) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.resultRepository = resultRepository;
        this.detailRepository = detailRepository;
        this.ahpRepository = ahpRepository;
        this.snapshotValueRepository = snapshotValueRepository;
        this.algorithmRegistry = algorithmRegistry;
        this.snapshotService = snapshotService;
        this.indicatorService = indicatorService;
        this.projectService = projectService;
        this.configService = configService;
        this.codeService = codeService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResult<EvaluationTask> search(Long projectId, String algorithm, String status,
                                            String keyword, LocalDateTime from, LocalDateTime to,
                                            int page, int size) {
        // 数据权限：null = 不限制（管理员 / ALL 范围）；空集合 = 无可见项目，直接返回空页
        // （空集合不能进 SQL 的 in ()，所以在这里短路）
        List<Long> ids = projectService.visibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<EvaluationTask> p = taskRepository.search(projectId, ids, blankToNull(algorithm), blankToNull(status),
                blankToNull(keyword), from, to,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public EvaluationTask get(Long id) {
        EvaluationTask task = taskRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BizException.notFound("评估任务不存在"));
        // 数据权限：任务所属项目必须对当前账号可见，否则直接 403（不能按 id 直读别人的任务）
        projectService.checkVisible(task.getProjectId());
        return task;
    }

    @Transactional(readOnly = true)
    public List<TaskStatusHistory> history(Long taskId) {
        return historyRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        EvaluationTask t = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task", t);
        m.put("parameters", JsonUtils.toMap(t.getParameters()));
        m.put("inputSummary", JsonUtils.toMap(t.getInputSummary()));
        m.put("history", history(id));
        m.put("resultCount", resultRepository.countByTaskId(id));
        m.put("consistency", ahpRepository.findByTaskIdOrderByIdAsc(id));
        return m;
    }

    // ------------------------------------------------------------------ 创建

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, String operator, Long operatorId) {
        Long projectId = asLong(body.get("projectId"));
        Long snapshotId = asLong(body.get("snapshotId"));
        Long versionId = asLong(body.get("indicatorVersionId"));
        String algorithm = asString(body.get("algorithm"));
        String taskName = asString(body.get("taskName"));

        if (projectId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择项目");
        }
        if (snapshotId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择数据快照");
        }
        if (algorithm == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择评估算法");
        }
        AlgorithmType type = AlgorithmType.of(algorithm);
        if (type == null || !algorithmRegistry.supports(type.getCode())) {
            throw new BizException(ErrorCode.ALGORITHM_NOT_SUPPORTED, "不支持的评估算法：" + algorithm);
        }
        projectService.checkVisible(projectId);
        DataSnapshot snapshot = snapshotService.get(snapshotId);
        IndicatorVersion version = indicatorService.get(versionId != null ? versionId
                : snapshot.getIndicatorVersionId());
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择指标体系版本");
        }
        if (!Objects.equals(snapshot.getIndicatorVersionId(), version.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "数据快照绑定的指标体系版本与所选版本不一致，请重新生成快照");
        }
        if (snapshot.getObjectCount() == null || snapshot.getObjectCount() == 0) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该数据快照中没有被评估对象");
        }
        if ((snapshot.getMissingCount() != null && snapshot.getMissingCount() > 0)
                && "BLOCK".equals(missingPolicy())) {
            throw new BizException(ErrorCode.MISSING_VALUE_BLOCKED,
                    "数据快照存在 " + snapshot.getMissingCount() + " 个缺失值，当前缺失值策略为阻止计算");
        }

        // 算法参数
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = body.get("parameters") instanceof Map<?, ?> pm
                ? new LinkedHashMap<>((Map<String, Object>) pm) : new LinkedHashMap<>();
        validateParameters(type, parameters, snapshot.getIndicatorVersionId());

        EvaluationTask task = new EvaluationTask();
        task.setTaskCode(codeService.next("TASK", "evaluation_task", "task_code"));
        task.setTaskName(taskName != null && !taskName.isBlank() ? taskName
                : (type.getDisplayName() + " - " + snapshot.getSnapshotCode()));
        task.setProjectId(projectId);
        task.setProjectCode(snapshot.getProjectCode());
        task.setIndicatorVersionId(version.getId());
        task.setIndicatorVersionCode(version.getVersionCode());
        task.setSnapshotId(snapshotId);
        task.setSnapshotCode(snapshot.getSnapshotCode());
        task.setAlgorithm(type.getCode());
        task.setAlgorithmVersion(algorithmRegistry.get(type.getCode()).algorithmVersion());
        task.setParameters(JsonUtils.toJson(parameters));
        task.setComboFlow(JsonUtils.toJson(parameters.get("comboFlow")));
        task.setStatus("DRAFT");
        task.setProgress(0);
        task.setSubmitBy(operator);
        task.setSubmitById(operatorId);
        task.setObjectCount(snapshot.getObjectCount());
        taskRepository.save(task);

        // 输入摘要（保存完整输入摘要，保证可复算）
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectCode", snapshot.getProjectCode());
        summary.put("snapshotCode", snapshot.getSnapshotCode());
        summary.put("indicatorVersionCode", version.getVersionCode());
        summary.put("algorithm", type.getCode());
        summary.put("algorithmVersion", task.getAlgorithmVersion());
        summary.put("objectCount", snapshot.getObjectCount());
        summary.put("indicatorCount", snapshot.getIndicatorCount());
        summary.put("missingCount", snapshot.getMissingCount());
        summary.put("outlierCount", snapshot.getOutlierCount());
        summary.put("parameters", parameters);
        summary.put("submittedAt", LocalDateTime.now().toString());
        task.setInputSummary(JsonUtils.toJson(summary));
        taskRepository.save(task);

        transition(task, "VALIDATING", "创建任务并执行输入校验", operator, operatorId);
        transition(task, "QUEUED", "输入校验通过，进入队列", operator, operatorId);
        task.setQueuedAt(LocalDateTime.now());
        taskRepository.save(task);

        // 异步执行
        Long taskId = task.getId();
        executeAsync(taskId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("taskCode", task.getTaskCode());
        out.put("status", task.getStatus());
        out.put("algorithm", task.getAlgorithm());
        out.put("algorithmVersion", task.getAlgorithmVersion());
        return out;
    }

    /** 参数预校验：AHP 判断矩阵、模糊评语集等，尽早返回字段级错误。 */
    private void validateParameters(AlgorithmType type, Map<String, Object> parameters, Long versionId) {
        switch (type) {
            case AHP -> {
                if (parameters.get("matrix") == null && parameters.get("nodes") == null) {
                    throw new BizException(ErrorCode.ALGORITHM_PARAM_INVALID,
                            "AHP 需要提供判断矩阵（parameters.matrix + criteria，或 parameters.nodes）");
                }
            }
            case FUZZY -> {
                // 评语集与等级量化分数缺省时使用系统默认值，允许不填
            }
            case COMBO -> {
                Object flow = parameters.get("comboFlow");
                if (flow == null) {
                    parameters.put("comboFlow", List.of("AHP", "WEIGHTED"));
                }
                if (parameters.get("matrix") == null && parameters.get("nodes") == null) {
                    throw new BizException(ErrorCode.ALGORITHM_PARAM_INVALID,
                            "组合流程若包含 AHP 阶段，需要提供判断矩阵");
                }
            }
            default -> {
                // WEIGHTED 无强制参数
            }
        }
    }

    // ------------------------------------------------------------------ 执行

    @Async("evaluationExecutor")
    public void executeAsync(Long taskId) {
        try {
            execute(taskId);
        } catch (Exception e) {
            log.error("任务 {} 执行异常", taskId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long taskId) {
        EvaluationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        if (cancelFlags.remove(taskId) != null) {
            transition(task, "CANCELED", "任务在开始执行前已被取消", "system", null);
            return;
        }
        long begin = System.currentTimeMillis();
        LocalDateTime start = LocalDateTime.now();
        task.setStartedAt(start);
        transition(task, "RUNNING", "开始执行算法", "system", null);
        task.setProgress(10);
        task.setCurrentStep("装配算法输入");
        taskRepository.save(task);

        try {
            AlgorithmRequest request = buildRequest(task);
            task.setProgress(30);
            task.setCurrentStep("调用算法引擎：" + task.getAlgorithm());
            taskRepository.save(task);

            AlgorithmEngine engine = algorithmRegistry.get(task.getAlgorithm());
            AlgorithmResult result = engine.evaluate(request);

            if (cancelFlags.remove(taskId) != null) {
                transition(task, "CANCELED", "任务执行过程中被取消", "system", null);
                return;
            }
            task.setProgress(80);
            task.setCurrentStep("保存评估结果");
            taskRepository.save(task);

            if (!result.isSuccess()) {
                task.setErrorCode(result.getErrorCode());
                task.setErrorMsg(truncate(result.getError(), 1900));
                task.setStackTrace(JsonUtils.toJson(result.getExtra()));
                transition(task, "FAILED", result.getError(), "system", null);
                task.setFinishedAt(LocalDateTime.now());
                task.setDurationMs(System.currentTimeMillis() - begin);
                task.setProgress(100);
                taskRepository.save(task);
                return;
            }

            // 清理该任务历史结果（重试场景）
            detailRepository.deleteByTaskId(taskId);
            resultRepository.deleteAll(resultRepository.findByTaskIdOrderByTotalScoreDesc(taskId));
            ahpRepository.deleteByTaskId(taskId);

            int count = saveResults(task, result);
            saveConsistency(task, result);

            task.setResultCount(count);
            task.setProgress(100);
            task.setCurrentStep("执行完成");
            task.setFinishedAt(LocalDateTime.now());
            task.setDurationMs(System.currentTimeMillis() - begin);
            task.setErrorCode(null);
            task.setErrorMsg(null);
            transition(task, "SUCCESS", "评估完成，共生成 " + count + " 条结果", "system", null);
            snapshotService.markUsed(task.getSnapshotId());
            log.info("任务 {} 执行成功，结果 {} 条，耗时 {} ms", task.getTaskCode(), count, task.getDurationMs());
        } catch (BizException e) {
            task.setErrorCode(e.getErrorCode().name());
            task.setErrorMsg(truncate(e.getMessage(), 1900));
            task.setFinishedAt(LocalDateTime.now());
            task.setDurationMs(System.currentTimeMillis() - begin);
            task.setProgress(100);
            transition(task, "FAILED", e.getMessage(), "system", null);
        } catch (Exception e) {
            log.error("任务 {} 执行失败", task.getTaskCode(), e);
            task.setErrorCode(ErrorCode.SYSTEM_ERROR.name());
            task.setErrorMsg(truncate(e.getMessage(), 1900));
            task.setFinishedAt(LocalDateTime.now());
            task.setDurationMs(System.currentTimeMillis() - begin);
            task.setProgress(100);
            transition(task, "FAILED", "系统异常：" + e.getMessage(), "system", null);
        }
    }

    /** 由快照 + 指标体系装配统一算法输入（附录 B.1）。 */
    private AlgorithmRequest buildRequest(EvaluationTask task) {
        DataSnapshot snapshot = snapshotService.get(task.getSnapshotId());
        List<Indicator> all = indicatorService.indicators(task.getIndicatorVersionId());
        List<Indicator> leaf = all.stream().filter(i -> Boolean.TRUE.equals(i.getLeaf()))
                .sorted(Comparator.comparing(i -> i.getSortOrder() == null ? 0 : i.getSortOrder()))
                .toList();
        if (leaf.isEmpty()) {
            leaf = all;
        }

        AlgorithmRequest request = new AlgorithmRequest();
        request.setTaskId(task.getTaskCode());
        request.setTaskCode(task.getTaskCode());
        request.setAlgorithm(task.getAlgorithm());
        request.setAlgorithmVersion(task.getAlgorithmVersion());
        request.setIndicatorVersion(task.getIndicatorVersionCode());
        request.setDataSnapshotId(task.getSnapshotCode());
        request.setParameters(JsonUtils.toMap(task.getParameters()));
        request.setInputRef(Map.of("snapshotId", task.getSnapshotCode()));
        request.setCrThreshold(properties.getAlgorithm().getAhpCrThreshold());
        request.setScoreScale(properties.getAlgorithm().getScoreScale());
        request.setAllowForceOnCrFail(paramBool(request.getParameters(), "allowForceOnCrFail",
                Boolean.parseBoolean(configValue("algorithm.allow.force", "false"))));

        for (Indicator i : leaf) {
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
            request.getIndicators().add(m);
        }

        List<String> objects = snapshotValueRepository.findObjectCodes(task.getSnapshotId());
        List<SnapshotValue> values = snapshotValueRepository.findBySnapshotIdOrderByObjectCodeAscIndicatorCodeAsc(task.getSnapshotId());
        Map<String, AlgorithmRequest.ObjectData> byObject = new LinkedHashMap<>();
        for (String code : objects) {
            AlgorithmRequest.ObjectData o = new AlgorithmRequest.ObjectData();
            o.setObjectCode(code);
            byObject.put(code, o);
        }
        for (SnapshotValue v : values) {
            AlgorithmRequest.ObjectData o = byObject.computeIfAbsent(v.getObjectCode(), k -> {
                AlgorithmRequest.ObjectData n = new AlgorithmRequest.ObjectData();
                n.setObjectCode(k);
                return n;
            });
            if (v.getObjectName() != null) {
                o.setObjectName(v.getObjectName());
            }
            if (Boolean.TRUE.equals(v.getMissing()) || v.getRawValue() == null) {
                o.getMissingIndicators().add(v.getIndicatorCode());
            } else {
                o.getValues().put(v.getIndicatorCode(), v.getRawValue().doubleValue());
            }
        }
        request.getObjects().addAll(byObject.values());
        return request;
    }

    private int saveResults(EvaluationTask task, AlgorithmResult result) {
        List<AlgorithmResult.ObjectResult> list = new ArrayList<>(result.getObjectResults());
        // 按得分降序，名次并列
        list.sort((a, b) -> {
            double x = a.getTotalScore() == null ? -1 : a.getTotalScore().doubleValue();
            double y = b.getTotalScore() == null ? -1 : b.getTotalScore().doubleValue();
            return Double.compare(y, x);
        });
        int count = 0;
        int rank = 1;
        for (int i = 0; i < list.size(); i++) {
            AlgorithmResult.ObjectResult or = list.get(i);
            if (i > 0) {
                double prev = list.get(i - 1).getTotalScore() == null ? -1
                        : list.get(i - 1).getTotalScore().doubleValue();
                double cur = or.getTotalScore() == null ? -1 : or.getTotalScore().doubleValue();
                if (Math.abs(prev - cur) > 1e-9) {
                    rank = i + 1;
                }
            }
            EvaluationResult er = new EvaluationResult();
            er.setTaskId(task.getId());
            er.setTaskCode(task.getTaskCode());
            er.setProjectId(task.getProjectId());
            er.setProjectCode(task.getProjectCode());
            er.setProjectName(task.getProjectName());
            er.setObjectCode(or.getObjectCode());
            er.setObjectName(or.getObjectName());
            er.setIndicatorVersionCode(task.getIndicatorVersionCode());
            er.setSnapshotCode(task.getSnapshotCode());
            er.setAlgorithm(task.getAlgorithm());
            er.setAlgorithmVersion(task.getAlgorithmVersion());
            er.setTotalScore(or.getTotalScore());
            er.setLevel(or.getLevel());
            er.setVector(JsonUtils.toJson(or.getVector()));
            er.setWeights(JsonUtils.toJson(or.getWeights()));
            er.setContributions(JsonUtils.toJson(or.getContributions()));
            er.setDetails(JsonUtils.toJson(or.getDetails()));
            er.setWarnings(JsonUtils.toJson(mergeWarnings(or.getWarnings(), result.getWarnings())));
            Map<String, Object> extra = new LinkedHashMap<>(or.getExtra() == null ? Map.of() : or.getExtra());
            extra.put("algorithmVersion", task.getAlgorithmVersion());
            extra.put("taskCode", task.getTaskCode());
            er.setExtra(JsonUtils.toJson(extra));
            er.setRanking(or.getRanking() == null ? rank : or.getRanking());
            er.setReviewed("NONE");
            er.setPublishStatus("DRAFT");
            resultRepository.save(er);

            List<ResultDetail> details = new ArrayList<>();
            int idx = 0;
            for (AlgorithmResult.DetailItem d : or.getDetails()) {
                ResultDetail rd = new ResultDetail();
                rd.setResultId(er.getId());
                rd.setTaskId(task.getId());
                rd.setObjectCode(or.getObjectCode());
                rd.setIndicatorCode(d.getIndicatorCode());
                rd.setIndicatorName(d.getIndicatorName());
                rd.setIndicatorLevel(d.getIndicatorLevel());
                rd.setParentCode(d.getParentCode());
                rd.setUnit(d.getUnit());
                rd.setDirection(d.getDirection());
                rd.setRawValue(d.getRawValue());
                rd.setNormalizedValue(d.getNormalizedValue());
                rd.setWeight(d.getWeight());
                rd.setContribution(d.getContribution());
                rd.setScore(d.getScore());
                rd.setGrade(d.getGrade());
                rd.setRankNo(++idx);
                rd.setRemark(d.getRemark());
                details.add(rd);
            }
            if (!details.isEmpty()) {
                detailRepository.saveAll(details);
            }
            count++;
        }
        return count;
    }

    private void saveConsistency(EvaluationTask task, AlgorithmResult result) {
        List<?> list = extractConsistency(result);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> mm = new HashMap<>();
            m.forEach((k, v) -> mm.put(String.valueOf(k), v));
            AhpConsistency c = new AhpConsistency();
            c.setTaskId(task.getId());
            c.setNodeCode(str(mm.get("nodeCode")));
            c.setNodeName(str(mm.get("nodeName")));
            c.setMatrixSize(asInt(mm.get("matrixSize")));
            c.setMatrixData(JsonUtils.toJson(mm.get("matrix")));
            c.setEigenVector(JsonUtils.toJson(mm.get("weights")));
            c.setLambdaMax(decimal(mm.get("lambdaMax")));
            c.setCi(decimal(mm.get("ci")));
            c.setRi(decimal(mm.get("ri")));
            c.setCr(decimal(mm.get("cr")));
            c.setPassed(Boolean.TRUE.equals(mm.get("passed")));
            c.setWarning(Boolean.TRUE.equals(mm.get("passed")) ? null
                    : String.format("CR=%.4f 未通过一致性检验", c.getCr() == null ? 0d : c.getCr().doubleValue()));
            ahpRepository.save(c);
        }
    }

    /** 从算法输出中提取 AHP 一致性检验记录（直接执行或组合流程阶段一）。 */
    private List<?> extractConsistency(AlgorithmResult result) {
        Map<String, Object> extra = result.getExtra();
        if (extra == null) {
            return null;
        }
        if (extra.get("consistency") instanceof List<?> direct) {
            return direct;
        }
        if (extra.get("stages") instanceof Map<?, ?> stages) {
            for (Object v : stages.values()) {
                if (v instanceof AlgorithmResult ar && ar.getExtra() != null
                        && ar.getExtra().get("consistency") instanceof List<?> inner) {
                    return inner;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 操作

    @Transactional
    public void cancel(Long taskId, String operator, Long operatorId) {
        EvaluationTask task = get(taskId);
        if (!List.of("QUEUED", "RUNNING", "VALIDATING", "DRAFT").contains(task.getStatus())) {
            throw new BizException(ErrorCode.TASK_STATUS_ILLEGAL,
                    "任务当前状态为 " + task.getStatus() + "，不允许取消");
        }
        cancelFlags.put(taskId, Boolean.TRUE);
        transition(task, "CANCELED", "用户取消任务", operator, operatorId);
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    @Transactional
    public Map<String, Object> retry(Long taskId, String operator, Long operatorId) {
        EvaluationTask origin = get(taskId);
        if (!List.of("FAILED", "CANCELED", "TIMEOUT").contains(origin.getStatus())) {
            throw new BizException(ErrorCode.TASK_STATUS_ILLEGAL,
                    "只有失败、超时或已取消的任务可以重试，当前状态：" + origin.getStatus());
        }
        EvaluationTask task = new EvaluationTask();
        task.setTaskCode(codeService.next("TASK", "evaluation_task", "task_code"));
        task.setTaskName(origin.getTaskName() + "（重试）");
        task.setProjectId(origin.getProjectId());
        task.setProjectCode(origin.getProjectCode());
        task.setProjectName(origin.getProjectName());
        task.setIndicatorVersionId(origin.getIndicatorVersionId());
        task.setIndicatorVersionCode(origin.getIndicatorVersionCode());
        task.setSnapshotId(origin.getSnapshotId());
        task.setSnapshotCode(origin.getSnapshotCode());
        task.setAlgorithm(origin.getAlgorithm());
        task.setAlgorithmVersion(origin.getAlgorithmVersion());
        task.setParameters(origin.getParameters());
        task.setComboFlow(origin.getComboFlow());
        task.setInputSummary(origin.getInputSummary());
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setRetryOf(origin.getId());
        task.setRetryCount((origin.getRetryCount() == null ? 0 : origin.getRetryCount()) + 1);
        task.setSubmitBy(operator);
        task.setSubmitById(operatorId);
        task.setObjectCount(origin.getObjectCount());
        task.setQueuedAt(LocalDateTime.now());
        taskRepository.save(task);
        transition(task, "QUEUED", "由任务 " + origin.getTaskCode() + " 重试生成新执行实例", operator, operatorId);
        executeAsync(task.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("taskCode", task.getTaskCode());
        out.put("retryOf", origin.getTaskCode());
        out.put("status", task.getStatus());
        return out;
    }

    @Transactional
    public void transition(EvaluationTask task, String toStatus, String reason, String operator, Long operatorId) {
        String from = task.getStatus();
        if (from != null && from.equals(toStatus)) {
            return;
        }
        task.setStatus(toStatus);
        taskRepository.save(task);
        TaskStatusHistory h = new TaskStatusHistory();
        h.setTaskId(task.getId());
        h.setFromStatus(from);
        h.setToStatus(toStatus);
        h.setReason(reason);
        h.setOperator(operator);
        h.setOperatorId(operatorId);
        historyRepository.save(h);
    }

    @Transactional(readOnly = true)
    public List<EvaluationTask> recent(int limit) {
        return taskRepository.findTop20ByDeletedFalseOrderByIdDesc().stream().limit(limit).toList();
    }

    private static List<String> mergeWarnings(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            for (String s : b) {
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private String missingPolicy() {
        return configService.get("data.missing.policy", "BLOCK");
    }

    private String configValue(String key, String def) {
        return configService.get(key, def);
    }

    private static boolean paramBool(Map<String, Object> m, String key, boolean def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(Object o) {
        Long l = asLong(o);
        return l == null ? null : l.intValue();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
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
}
