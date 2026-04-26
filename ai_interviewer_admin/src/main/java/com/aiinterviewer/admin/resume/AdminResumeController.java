package com.aiinterviewer.admin.resume;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/resumes")
@RequiredArgsConstructor
public class AdminResumeController {

    private final AdminResumeService adminResumeService;

    @GetMapping
    public Result<PageResult<AdminResumeService.AdminResumeListItem>> listResumes(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer parseStatus,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AdminResumeService.AdminResumeQuery query = new AdminResumeService.AdminResumeQuery();
        query.setUserId(userId);
        query.setParseStatus(parseStatus);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(adminResumeService.listResumes(query));
    }

    @GetMapping("/{resumeId}")
    public Result<AdminResumeService.AdminResumeDetail> getResumeDetail(@PathVariable Long resumeId) {
        return Result.success(adminResumeService.getResumeDetail(resumeId));
    }
}
