package com.aiinterviewer.admin.job;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final AdminJobService adminJobService;

    @GetMapping
    public Result<PageResult<AdminJobService.AdminJobListItem>> listJobs(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String skill,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AdminJobService.AdminJobQuery query = new AdminJobService.AdminJobQuery();
        query.setTitle(title);
        query.setCompany(company);
        query.setStatus(status);
        query.setSkill(skill);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(adminJobService.listJobs(query));
    }

    @GetMapping("/{jobId}")
    public Result<AdminJobService.AdminJobDetail> getJobDetail(@PathVariable Long jobId) {
        return Result.success(adminJobService.getJobDetail(jobId));
    }

    @PostMapping
    public Result<Long> createJob(@RequestBody AdminJobService.AdminJobUpsertRequest request) {
        return Result.success(adminJobService.createJob(request));
    }

    @PutMapping("/{jobId}")
    public Result<Void> updateJob(
            @PathVariable Long jobId,
            @RequestBody AdminJobService.AdminJobUpsertRequest request) {
        adminJobService.updateJob(jobId, request);
        return Result.success();
    }

    @PatchMapping("/{jobId}/close")
    public Result<Void> closeJob(@PathVariable Long jobId) {
        adminJobService.closeJob(jobId);
        return Result.success();
    }

    @PatchMapping("/{jobId}/reopen")
    public Result<Void> reopenJob(@PathVariable Long jobId) {
        adminJobService.reopenJob(jobId);
        return Result.success();
    }

    @PutMapping("/{jobId}/questions")
    public Result<Void> configureQuestions(
            @PathVariable Long jobId,
            @RequestBody AdminJobService.JobQuestionConfigRequest request) {
        adminJobService.configureQuestions(jobId, request);
        return Result.success();
    }
}
