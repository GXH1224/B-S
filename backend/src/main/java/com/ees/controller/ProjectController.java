package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.Project;
import com.ees.domain.ProjectMember;
import com.ees.security.SecurityUtils;
import com.ees.service.ProjectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目管理接口。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** GET /api/projects 查询用户可见项目 */
    @GetMapping
    @PreAuthorize("hasAuthority('project:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<Project>> search(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(projectService.search(keyword, status, page, size));
    }

    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.ok(projectService.options());
    }

    /** GET /api/projects/all 项目全量列表（下拉选择用）。与 /api/projects 保持同一鉴权口径。 */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('project:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Project>> all() {
        return ApiResponse.ok(projectService.all());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Project> get(@PathVariable Long id) {
        return ApiResponse.ok(projectService.get(id));
    }

    @GetMapping("/{id}/statistics")
    public ApiResponse<Map<String, Object>> statistics(@PathVariable Long id) {
        return ApiResponse.ok(projectService.statistics(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Project> create(@RequestBody Project project) {
        project.setId(null);
        return ApiResponse.ok(projectService.save(project, SecurityUtils.currentUsername()), "项目创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        return ApiResponse.ok(projectService.save(project, SecurityUtils.currentUsername()), "项目更新成功");
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('project:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Project> archive(@PathVariable Long id) {
        return ApiResponse.ok(projectService.archive(id, SecurityUtils.currentUsername()), "项目已归档");
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('project:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Project> activate(@PathVariable Long id) {
        return ApiResponse.ok(projectService.activate(id, SecurityUtils.currentUsername()), "项目已启用");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<ProjectMember>> members(@PathVariable Long id) {
        return ApiResponse.ok(projectService.members(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:member') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> saveMembers(@PathVariable Long id, @RequestBody List<ProjectMember> members) {
        projectService.saveMembers(id, members, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }
}
