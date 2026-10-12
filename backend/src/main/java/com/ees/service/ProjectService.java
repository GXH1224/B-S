package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.PageLimits;
import com.ees.common.PageResult;
import com.ees.domain.Project;
import com.ees.domain.ProjectMember;
import com.ees.domain.SysUser;
import com.ees.repository.DataBatchRepository;
import com.ees.repository.EvaluationResultRepository;
import com.ees.repository.EvaluationTaskRepository;
import com.ees.repository.ProjectMemberRepository;
import com.ees.repository.ProjectRepository;
import com.ees.repository.SysUserRepository;
import com.ees.security.LoginUser;
import com.ees.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目管理服务（方案 4.2）。保留项目操作记录，支持归档，不直接物理删除已产生评估结果的项目。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final SysUserRepository userRepository;
    private final EvaluationTaskRepository taskRepository;
    private final EvaluationResultRepository resultRepository;
    private final DataBatchRepository batchRepository;
    private final CodeService codeService;

    /** 无分页接口（GET /api/projects/all）返回条数上限，防止一次拉全表把内存打爆。 */
    private static final int MAX_ALL_SIZE = 500;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          SysUserRepository userRepository, EvaluationTaskRepository taskRepository,
                          EvaluationResultRepository resultRepository, DataBatchRepository batchRepository,
                          CodeService codeService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
        this.batchRepository = batchRepository;
        this.codeService = codeService;
    }

    /** 当前用户可见项目 ID；返回 null 表示不限制（ALL 范围）。 */
    public List<Long> visibleProjectIds() {
        LoginUser u = SecurityUtils.require();
        if (u.isSuperAdmin() || "ALL".equals(u.getDataScope())) {
            return null;
        }
        List<Long> ids = memberRepository.findByUserId(u.getUserId()).stream()
                .map(ProjectMember::getProjectId).collect(Collectors.toList());
        List<Long> owned = projectRepository.findAllByDeletedFalseOrderByIdDesc().stream()
                .filter(p -> u.getUserId().equals(p.getOwnerId()))
                .map(Project::getId).collect(Collectors.toList());
        for (Long id : owned) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public void checkVisible(Long projectId) {
        List<Long> ids = visibleProjectIds();
        if (ids != null && !ids.contains(projectId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
    }

    @Transactional(readOnly = true)
    public PageResult<Project> search(String keyword, String status, int page, int size) {
        size = PageLimits.size(size);
        List<Long> ids = visibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<Project> p = projectRepository.search(
                blankToNull(keyword), blankToNull(status), ids,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public Project get(Long id) {
        checkVisible(id);
        return projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BizException.notFound("项目不存在"));
    }

    @Transactional(readOnly = true)
    public List<Project> all() {
        List<Long> ids = visibleProjectIds();
        // 在数据库层就限制条数（而非查全表后在内存里截断）
        List<Project> list = projectRepository
                .findAllByDeletedFalse(PageRequest.of(0, MAX_ALL_SIZE, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
        if (ids == null) {
            return list;
        }
        return list.stream().filter(p -> ids.contains(p.getId())).collect(Collectors.toList());
    }

    @Transactional
    public Project save(Project input, String operator) {
        Project project;
        boolean creating = input.getId() == null;
        if (!creating) {
            project = projectRepository.findByIdAndDeletedFalse(input.getId())
                    .orElseThrow(() -> BizException.notFound("项目不存在"));
            checkVisible(project.getId());
            if (!project.getProjectCode().equals(input.getProjectCode())
                    && projectRepository.existsByProjectCodeAndDeletedFalse(input.getProjectCode())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "项目编码已存在：" + input.getProjectCode());
            }
        } else {
            if (input.getProjectCode() == null || input.getProjectCode().isBlank()) {
                input.setProjectCode(codeService.next("PRJ", "project", "project_code"));
            }
            if (projectRepository.existsByProjectCodeAndDeletedFalse(input.getProjectCode())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "项目编码已存在：" + input.getProjectCode());
            }
            project = new Project();
            project.setCreatedBy(operator);
        }
        project.setProjectCode(input.getProjectCode());
        project.setProjectName(input.getProjectName());
        project.setProjectType(input.getProjectType());
        project.setDescription(input.getDescription());
        // 负责人：创建时未指定则默认取当前登录用户；更新时若被清空则保留原负责人。
        // 为什么必须兜底：非管理员角色的数据范围是 PROJECT，可见项目集合由
        // visibleProjectIds() 决定 —— 只认"本人是 project_member 成员"或"本人是 owner_id"。
        // 若项目既没有负责人、创建者也没落成员记录，创建者本人都会看不到自己刚建的项目。
        if (input.getOwnerId() != null) {
            project.setOwnerId(input.getOwnerId());
            project.setOwnerName(input.getOwnerName());
        } else if (creating) {
            LoginUser current = SecurityUtils.currentUser().orElse(null);
            if (current != null) {
                project.setOwnerId(current.getUserId());
                project.setOwnerName(current.getRealName() != null && !current.getRealName().isBlank()
                        ? current.getRealName() : current.getUsername());
            }
        }
        project.setOrgName(input.getOrgName());
        if (input.getStatus() != null) {
            project.setStatus(input.getStatus());
        }
        project.setStartDate(input.getStartDate());
        project.setEndDate(input.getEndDate());
        project.setRemark(input.getRemark());
        project.setUpdatedBy(operator);
        Project saved = projectRepository.save(project);

        // 负责人自动加入项目成员（创建与更新都执行，保持既有行为）
        if (saved.getOwnerId() != null) {
            ensureMember(saved.getId(), saved.getOwnerId(), "MANAGER");
        }

        // 创建者也要落一条成员记录。只写负责人是不够的：
        // 非管理员的可见范围来自 project_member，若"创建者 ≠ 负责人"（例如评估人员建项目、
        // 把负责人指派给他人），创建者本人将看不到自己刚创建的项目，且 checkVisible 会直接拒绝其访问。
        // 对 SYSTEM_ADMIN / 数据范围 ALL 的用户不必写（他们本来就不受限制），避免污染成员列表。
        if (creating) {
            LoginUser current = SecurityUtils.currentUser().orElse(null);
            if (current != null
                    && !current.isSuperAdmin()
                    && !"ALL".equals(current.getDataScope())
                    && !current.getUserId().equals(saved.getOwnerId())) {
                ensureMember(saved.getId(), current.getUserId(), "EVALUATOR");
            }
        }
        return saved;
    }

    /**
     * 幂等地补一条项目成员记录：已存在则不覆盖，避免冲掉手工在「成员配置」里设好的成员角色。
     *
     * @param memberRole MANAGER（负责人）或 EVALUATOR（创建者兜底）
     */
    private void ensureMember(Long projectId, Long userId, String memberRole) {
        if (memberRepository.findByProjectIdAndUserId(projectId, userId).isPresent()) {
            return;
        }
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setUsername(userRepository.findById(userId).map(SysUser::getUsername).orElse(null));
        m.setMemberRole(memberRole);
        memberRepository.save(m);
    }

    @Transactional
    public Project archive(Long id, String operator) {
        Project p = get(id);
        p.setStatus("ARCHIVED");
        p.setArchivedAt(LocalDateTime.now());
        p.setUpdatedBy(operator);
        return projectRepository.save(p);
    }

    @Transactional
    public Project activate(Long id, String operator) {
        Project p = get(id);
        p.setStatus("ACTIVE");
        p.setArchivedAt(null);
        p.setUpdatedBy(operator);
        return projectRepository.save(p);
    }

    /** 逻辑删除：已产生评估结果的项目禁止删除，只能归档。 */
    @Transactional
    public void delete(Long id, String operator) {
        Project p = get(id);
        long results = resultRepository.countByProjectId(id);
        if (results > 0) {
            throw new BizException(ErrorCode.BUSINESS_ERROR,
                    "该项目已产生 " + results + " 条评估结果，不允许删除，请改用归档");
        }
        p.setDeleted(true);
        p.setUpdatedBy(operator);
        projectRepository.save(p);
    }

    @Transactional
    public void saveMembers(Long projectId, List<ProjectMember> members, String operator) {
        checkVisible(projectId);
        memberRepository.deleteByProjectId(projectId);
        if (members == null) {
            return;
        }
        for (ProjectMember m : members) {
            m.setId(null);
            m.setProjectId(projectId);
            m.setCreatedAt(LocalDateTime.now());
            if (m.getUsername() == null && m.getUserId() != null) {
                m.setUsername(userRepository.findById(m.getUserId())
                        .map(SysUser::getUsername).orElse(null));
            }
            memberRepository.save(m);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> members(Long projectId) {
        checkVisible(projectId);
        return memberRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics(Long projectId) {
        Project p = get(projectId);
        Map<String, Object> m = new HashMap<>();
        m.put("projectId", projectId);
        m.put("projectCode", p.getProjectCode());
        m.put("projectName", p.getProjectName());
        m.put("status", p.getStatus());
        m.put("taskCount", taskRepository.findByProjectIdAndDeletedFalseOrderByIdDesc(projectId).size());
        m.put("resultCount", resultRepository.countByProjectId(projectId));
        m.put("batchCount", batchRepository.countByProjectIdAndDeletedFalse(projectId));
        m.put("publishedCount", resultRepository.countByProjectIdAndPublishStatus(projectId, "PUBLISHED"));
        m.put("avgScore", resultRepository.avgScoreByProject(projectId));
        return m;
    }

    /** 下拉选择用精简列表。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> options() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Project p : all()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("code", p.getProjectCode());
            m.put("name", p.getProjectName());
            m.put("status", p.getStatus());
            out.add(m);
        }
        return out;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
