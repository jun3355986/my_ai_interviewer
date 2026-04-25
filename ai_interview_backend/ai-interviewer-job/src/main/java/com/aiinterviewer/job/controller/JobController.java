package com.aiinterviewer.job.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.job.dto.*;
import com.aiinterviewer.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 职位控制器
 */
@Tag(name = "职位管理", description = "职位CRUD和匹配分析接口")
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalStateException("用户未登录");
        }
        return Long.parseLong(userIdStr);
    }

    /**
     * 创建职位
     */
    @Operation(summary = "创建职位")
    @PostMapping
    public Result<JobDTO> createJob(@RequestBody JobRequest request, HttpServletRequest req) {
        Long userId = getCurrentUserId(req);
        JobDTO job = jobService.createJob(userId, request);
        return Result.success(job);
    }

    /**
     * 更新职位
     */
    @Operation(summary = "更新职位")
    @PutMapping("/{id}")
    public Result<JobDTO> updateJob(@PathVariable("id") Long jobId,
            @RequestBody JobRequest request,
            HttpServletRequest req) {
        Long userId = getCurrentUserId(req);
        JobDTO job = jobService.updateJob(jobId, userId, request);
        return Result.success(job);
    }

    /**
     * 获取职位详情
     */
    @Operation(summary = "获取职位详情")
    @GetMapping("/{id}")
    public Result<JobDTO> getJob(@PathVariable("id") Long jobId) {
        JobDTO job = jobService.getJob(jobId);
        return Result.success(job);
    }

    /**
     * 获取职位列表
     */
    @Operation(summary = "获取职位列表")
    @GetMapping
    public Result<List<JobDTO>> listJobs() {
        List<JobDTO> jobs = jobService.listJobs();
        return Result.success(jobs);
    }

    /**
     * 搜索职位
     */
    @Operation(summary = "搜索职位")
    @GetMapping("/search")
    public Result<List<JobDTO>> searchJobs(@RequestParam("keyword") String keyword) {
        List<JobDTO> jobs = jobService.searchJobs(keyword);
        return Result.success(jobs);
    }

    /**
     * 获取用户创建的职位
     */
    @Operation(summary = "获取我创建的职位")
    @GetMapping("/my")
    public Result<List<JobDTO>> listMyJobs(HttpServletRequest req) {
        Long userId = getCurrentUserId(req);
        List<JobDTO> jobs = jobService.listJobsByUser(userId);
        return Result.success(jobs);
    }

    /**
     * 关闭职位
     */
    @Operation(summary = "关闭职位")
    @PutMapping("/{id}/close")
    public Result<Void> closeJob(@PathVariable("id") Long jobId, HttpServletRequest req) {
        Long userId = getCurrentUserId(req);
        jobService.closeJob(jobId, userId);
        return Result.success(null);
    }

    /**
     * 删除职位
     */
    @Operation(summary = "删除职位")
    @DeleteMapping("/{id}")
    public Result<Void> deleteJob(@PathVariable("id") Long jobId, HttpServletRequest req) {
        Long userId = getCurrentUserId(req);
        jobService.deleteJob(jobId, userId);
        return Result.success(null);
    }

    /**
     * 职位-简历匹配度分析
     */
    @Operation(summary = "匹配度分析")
    @PostMapping("/{id}/match")
    public Result<MatchAnalysisResponse> analyzeMatch(@PathVariable("id") Long jobId,
            @RequestBody MatchAnalysisRequest request) {
        MatchAnalysisResponse response = jobService.analyzeMatch(jobId, request.getResumeContent());
        return Result.success(response);
    }
}
