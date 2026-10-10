package com.ees.service;

import com.ees.algorithm.AlgorithmRegistry;
import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.domain.EvaluationResult;
import com.ees.domain.EvaluationTask;
import com.ees.repository.DataBatchRepository;
import com.ees.repository.DataSnapshotRepository;
import com.ees.repository.EvaluationResultRepository;
import com.ees.repository.EvaluationTaskRepository;
import com.ees.repository.IndicatorVersionRepository;
import com.ees.repository.ProjectRepository;
import com.ees.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 综合驾驶舱服务（方案 7.4）。
 *
 * <p>只展示已授权数据：领导与结果页面突出综合得分、等级、排名、趋势和异常。</p>
 */
@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final EvaluationTaskRepository taskRepository;
    private final EvaluationResultRepository resultRepository;
    private final DataBatchRepository batchRepository;
    private final DataSnapshotRepository snapshotRepository;
    private final IndicatorVersionRepository versionRepository;
    private final SysUserRepository userRepository;
    private final ProjectService projectService;
    private final AlgorithmRegistry algorithmRegistry;

    public DashboardService(ProjectRepository projectRepository, EvaluationTaskRepository taskRepository,
                            EvaluationResultRepository resultRepository, DataBatchRepository batchRepository,
                            DataSnapshotRepository snapshotRepository,
                            IndicatorVersionRepository versionRepository,
                            SysUserRepository userRepository, ProjectService projectService,
                            AlgorithmRegistry algorithmRegistry) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
        this.batchRepository = batchRepository;
        this.snapshotRepository = snapshotRepository;
        this.versionRepository = versionRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.algorithmRegistry = algorithmRegistry;
    }

    /** 驾驶舱总览。 */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long projectId) {
        return overview(projectId, null, null);
    }

    /**
     * 驾驶舱总览（可指定统计区间）。
     *
     * <p>「任务趋势」折线图与「评判等级分布」饼图**共用同一个统计区间** {@code from}~{@code to}（含首尾两天），
     * 由前端传入；两者都缺省时默认近 7 天。区间只影响这两张图，
     * 上方指标卡（项目数 / 任务数 / 结果数 / 平均分等）始终是"当前总量"，不随区间变化。</p>
     *
     * @param from 统计开始日期（含），为空则取 {@code to} 往前 6 天
     * @param to   统计结束日期（含），为空则取今天
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long projectId, LocalDate from, LocalDate to) {
        // 区间解析与校验：默认近 7 天；限制最长 366 天，避免超长区间把按天分桶撑爆
        LocalDate rangeEnd = to != null ? to : LocalDate.now();
        LocalDate rangeStart = from != null ? from : rangeEnd.minusDays(6);
        if (rangeStart.isAfter(rangeEnd)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统计开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1 > 366) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统计区间最长支持 366 天");
        }
        LocalDateTime rangeFromTs = rangeStart.atStartOfDay();
        LocalDateTime rangeToTs = rangeEnd.plusDays(1).atStartOfDay();   // 左闭右开，含结束日当天

        // 数据权限口径（与 ProjectService 一致）：
        //   visible == null  → 超级管理员 / ALL 范围，表示「不限制」，可以看全部项目的统计；
        //   visible == [..]  → 只能看这些项目，可见集合可能是空的（一个项目都没有）。
        List<Long> visible = projectService.visibleProjectIds();
        if (projectId != null) {
            // 指定了不可见的项目时直接 403：否则可以借筛选参数读到别人项目的统计
            // （与 ProjectService.get(id) 的 checkVisible 同一口径）
            projectService.checkVisible(projectId);
        }
        // ids: null = 全部项目；[x] = 指定项目；[] = 无可见项目
        List<Long> ids = projectId != null ? List.of(projectId) : visible;
        boolean all = ids == null;
        boolean none = !all && ids.isEmpty();

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("projectCount", all
                ? projectRepository.countByStatusAndDeletedFalse("ACTIVE")
                        + projectRepository.countByStatusAndDeletedFalse("DRAFT")
                : (projectId != null ? 1 : ids.size()));
        m.put("activeProjectCount", countProjectsByStatus("ACTIVE", all, none, ids));
        m.put("archivedProjectCount", countProjectsByStatus("ARCHIVED", all, none, ids));
        m.put("indicatorVersionCount", all
                ? versionRepository.countByStatusAndDeletedFalse("PUBLISHED")
                : (none
                        // 一个项目都看不到时，公共版本（projectId 为空）仍然可见
                        ? versionRepository.countByProjectIdIsNullAndStatusAndDeletedFalse("PUBLISHED")
                        : versionRepository.countVisibleByStatus("PUBLISHED", ids)));
        m.put("userCount", userRepository.findAllByDeletedFalseOrderByIdAsc().size());
        m.put("enabledUserCount", userRepository.countByStatusAndDeletedFalse("ENABLED"));
        m.put("algorithmCount", algorithmRegistry.supported().size());
        m.put("algorithms", algorithmRegistry.supported());

        long taskRunning = taskCountByStatus("RUNNING", all, none, ids)
                + taskCountByStatus("QUEUED", all, none, ids);
        long taskSuccess = taskCountByStatus("SUCCESS", all, none, ids)
                + taskCountByStatus("PUBLISHED", all, none, ids)
                + taskCountByStatus("PENDING_REVIEW", all, none, ids);
        m.put("taskTotal", all ? taskRepository.count()
                : (none ? 0 : taskRepository.countByProjectIdInAndDeletedFalse(ids)));
        m.put("taskRunning", taskRunning);
        m.put("taskSuccess", taskSuccess);
        m.put("taskFailed", taskCountByStatus("FAILED", all, none, ids));
        m.put("taskPendingReview", taskCountByStatus("PENDING_REVIEW", all, none, ids));
        m.put("taskToday", all
                ? taskRepository.countByCreatedAtAfterAndDeletedFalse(LocalDate.now().atStartOfDay())
                : (none ? 0 : taskRepository.countByProjectIdInAndCreatedAtAfterAndDeletedFalse(
                        ids, LocalDate.now().atStartOfDay())));

        m.put("batchCount", all ? batchRepository.count()
                : (none ? 0 : batchRepository.countByProjectIdInAndDeletedFalse(ids)));
        m.put("snapshotCount", all ? snapshotRepository.count()
                : (none ? 0 : snapshotRepository.countByProjectIdIn(ids)));
        m.put("resultCount", all ? resultRepository.count()
                : (none ? 0 : resultRepository.countByProjectIdIn(ids)));
        // 「全部项目」视图原先把这两项写死为 0 / null，导致「平均得分」卡片恒显示 “-”、
        // 「已发布」恒为 0（即使确有已发布结果也不会变）。此处补上与按项目分支对称的统计，
        // 并且同样按数据权限过滤：管理员（all）看全库，项目级用户只看可见项目。
        m.put("publishedResultCount", all
                ? resultRepository.countByPublishStatus("PUBLISHED")
                : (none ? 0 : resultRepository.countByProjectIdInAndPublishStatus(ids, "PUBLISHED")));
        m.put("avgScore", all
                ? resultRepository.avgScoreAll()
                : (none ? null : resultRepository.avgScoreByProjects(ids)));

        // 等级分布：与任务趋势共用同一统计区间（按结果创建时间过滤），前端改区间时两张图一起变
        List<Object[]> levelStats = none
                ? List.of()
                : resultRepository.countGroupByLevelInRange(all ? null : ids, rangeFromTs, rangeToTs);
        List<Map<String, Object>> levels = new ArrayList<>();
        for (Object[] row : levelStats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", row[0] == null ? "未评级" : row[0]);
            item.put("count", row[1]);
            levels.add(item);
        }
        m.put("levelDistribution", levels);

        List<Object[]> algoStats = all
                ? resultRepository.countGroupByAlgorithm()
                : (none ? List.of() : resultRepository.countGroupByAlgorithmIn(ids));
        List<Map<String, Object>> algos = new ArrayList<>();
        for (Object[] row : algoStats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("algorithm", row[0]);
            item.put("algorithmLabel", ResultService.algorithmLabel((String) row[0]));
            item.put("algorithmVersion", row[1]);
            item.put("count", row[2]);
            algos.add(item);
        }
        m.put("algorithmUsage", algos);

        // 最新结果
        List<Map<String, Object>> latest = new ArrayList<>();
        List<EvaluationResult> latestResults;
        if (all) {
            latestResults = resultRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 10,
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC, "id"))).getContent();
        } else if (none) {
            latestResults = List.of();
        } else {
            latestResults = resultRepository.findTop10ByProjectIdInOrderByIdDesc(ids);
        }
        for (EvaluationResult r : latestResults) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("taskId", r.getTaskId());
            item.put("taskCode", r.getTaskCode());
            item.put("projectCode", r.getProjectCode());
            item.put("objectCode", r.getObjectCode());
            item.put("objectName", r.getObjectName());
            item.put("totalScore", r.getTotalScore());
            item.put("level", r.getLevel());
            item.put("ranking", r.getRanking());
            item.put("algorithm", r.getAlgorithm());
            item.put("algorithmLabel", ResultService.algorithmLabel(r.getAlgorithm()));
            item.put("createdAt", r.getCreatedAt());
            latest.add(item);
        }
        m.put("latestResults", latest);

        // 任务告警用的「最近 20 条任务」（告警不随统计区间变化，始终看最近的）
        List<EvaluationTask> recentTasks = all
                ? taskRepository.findTop20ByDeletedFalseOrderByIdDesc()
                : (none ? List.of() : taskRepository.findTop20ByProjectIdInAndDeletedFalseOrderByIdDesc(ids));

        // 任务告警
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (EvaluationTask t : recentTasks) {
            if ("FAILED".equals(t.getStatus()) || "TIMEOUT".equals(t.getStatus())) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("taskId", t.getId());
                a.put("taskCode", t.getTaskCode());
                a.put("taskName", t.getTaskName());
                a.put("status", t.getStatus());
                a.put("errorCode", t.getErrorCode());
                a.put("errorMsg", t.getErrorMsg());
                a.put("createdAt", t.getCreatedAt());
                alerts.add(a);
            }
            if (alerts.size() >= 5) {
                break;
            }
        }
        m.put("alerts", alerts);

        // 近期趋势（按天分桶，区间由前端决定、默认近 7 天）
        // 注意：这里必须按区间查库，不能复用上面的 recentTasks —— 那只取最近 20 条，
        // 一旦区间拉长（近一个月 / 近一年）就会漏计。
        Map<String, Integer> byDay = new LinkedHashMap<>();
        for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
            byDay.put(d.toString(), 0);
        }
        List<EvaluationTask> trendTasks = none
                ? List.of()
                : taskRepository.findForTrend(all ? null : ids, rangeFromTs, rangeToTs);
        for (EvaluationTask t : trendTasks) {
            if (t.getCreatedAt() == null) {
                continue;
            }
            String key = t.getCreatedAt().toLocalDate().toString();
            if (byDay.containsKey(key)) {
                byDay.merge(key, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        byDay.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", k);
            item.put("taskCount", v);
            trend.add(item);
        });
        m.put("taskTrend", trend);

        // 项目态势
        List<Map<String, Object>> projectStats = new ArrayList<>();
        for (var p : projectService.all()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("projectCode", p.getProjectCode());
            item.put("projectName", p.getProjectName());
            item.put("status", p.getStatus());
            item.put("taskCount", taskRepository.findByProjectIdAndDeletedFalseOrderByIdDesc(p.getId()).size());
            item.put("resultCount", resultRepository.countByProjectId(p.getId()));
            BigDecimal avg = resultRepository.avgScoreByProject(p.getId());
            item.put("avgScore", avg == null ? null : avg.setScale(2, RoundingMode.HALF_UP));
            projectStats.add(item);
        }
        m.put("projects", projectStats);
        // 回显本次生效的统计区间（前端据此显示标题与日期控件，避免前后端理解不一致）
        m.put("rangeFrom", rangeStart.toString());
        m.put("rangeTo", rangeEnd.toString());
        m.put("generatedAt", LocalDateTime.now().toString());
        return m;
    }

    /** 可见项目集合内按状态统计项目数；all = 不限制（管理员 / ALL 范围）。 */
    private long countProjectsByStatus(String status, boolean all, boolean none, List<Long> ids) {
        if (all) {
            return projectRepository.countByStatusAndDeletedFalse(status);
        }
        return none ? 0 : projectRepository.countByStatusAndDeletedFalseAndIdIn(status, ids);
    }

    /** 可见项目集合内按状态统计任务数；all = 不限制（管理员 / ALL 范围）。 */
    private long taskCountByStatus(String status, boolean all, boolean none, List<Long> ids) {
        if (all) {
            return taskRepository.countByStatusAndDeletedFalse(status);
        }
        return none ? 0 : taskRepository.countByProjectIdInAndStatusAndDeletedFalse(ids, status);
    }
}
