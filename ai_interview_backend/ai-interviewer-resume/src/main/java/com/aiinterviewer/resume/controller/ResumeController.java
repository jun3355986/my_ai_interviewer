package com.aiinterviewer.resume.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.resume.dto.ParseResumeResponse;
import com.aiinterviewer.resume.dto.ResumeDTO;
import com.aiinterviewer.resume.dto.ResumeParseRequest;
import com.aiinterviewer.resume.dto.ResumeUploadRequest;
import com.aiinterviewer.resume.dto.VersionDTO;
import com.aiinterviewer.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历控制器
 */
@Tag(name = "简历管理", description = "简历上传、解析、查询等接口")
@RestController
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * 获取当前用户ID (从请求头获取)
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalStateException("用户未登录");
        }
        return Long.parseLong(userIdStr);
    }

    /**
     * 上传简历
     */
    @Operation(summary = "上传简历")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeDTO> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "setAsDefault", required = false) Boolean setAsDefault,
            @RequestParam(value = "remark", required = false) String remark,
            HttpServletRequest request) {

        Long userId = getCurrentUserId(request);
        ResumeUploadRequest uploadRequest = new ResumeUploadRequest();
        uploadRequest.setSetAsDefault(setAsDefault);
        uploadRequest.setRemark(remark);

        ResumeDTO resume = resumeService.uploadResume(userId, file, uploadRequest);
        return Result.success(resume);
    }

    /**
     * 解析简历
     */
    @Operation(summary = "解析简历")
    @PostMapping("/{id}/parse")
    public Result<ParseResumeResponse> parseResume(
            @PathVariable("id") Long resumeId,
            @RequestBody(required = false) ResumeParseRequest request,
            HttpServletRequest requestCtx) {

        Long userId = getCurrentUserId(requestCtx);
        if (request == null) {
            request = new ResumeParseRequest();
        }

        ParseResumeResponse response = resumeService.parseResume(resumeId, userId, request);
        return Result.success(response);
    }

    /**
     * 获取简历列表
     */
    @Operation(summary = "获取简历列表")
    @GetMapping
    public Result<List<ResumeDTO>> listResumes(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<ResumeDTO> resumes = resumeService.listResumes(userId);
        return Result.success(resumes);
    }

    /**
     * 获取简历详情
     */
    @Operation(summary = "获取简历详情")
    @GetMapping("/{id}")
    public Result<ResumeDTO> getResume(@PathVariable("id") Long resumeId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        ResumeDTO resume = resumeService.getResume(resumeId, userId);
        return Result.success(resume);
    }

    /**
     * 删除简历
     */
    @Operation(summary = "删除简历")
    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable("id") Long resumeId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        resumeService.deleteResume(resumeId, userId);
        return Result.success(null);
    }

    /**
     * 设为默认简历
     */
    @Operation(summary = "设为默认简历")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable("id") Long resumeId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        resumeService.setDefault(resumeId, userId);
        return Result.success(null);
    }

    /**
     * 获取版本历史
     */
    @Operation(summary = "获取版本历史")
    @GetMapping("/{id}/versions")
    public Result<List<VersionDTO>> getVersionHistory(@PathVariable("id") Long resumeId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<VersionDTO> versions = resumeService.getVersionHistory(resumeId, userId);
        return Result.success(versions);
    }

    /**
     * 获取默认简历
     */
    @Operation(summary = "获取默认简历")
    @GetMapping("/default")
    public Result<ResumeDTO> getDefaultResume(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        ResumeDTO resume = resumeService.getDefaultResume(userId);
        return Result.success(resume);
    }
}
