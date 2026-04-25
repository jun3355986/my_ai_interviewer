package com.aiinterviewer.admin.interview;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.interview.dto.InterviewDiagnosisResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/interviews")
@RequiredArgsConstructor
public class AdminInterviewController {

    private final AdminInterviewService adminInterviewService;

    @GetMapping
    public Result<PageResult<AdminInterviewService.AdminInterviewListItem>> listInterviews(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startedTo,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AdminInterviewService.AdminInterviewQuery query = new AdminInterviewService.AdminInterviewQuery();
        query.setUserId(userId);
        query.setJobId(jobId);
        query.setStage(stage);
        query.setStatus(status);
        query.setStartedFrom(startedFrom);
        query.setStartedTo(startedTo);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(adminInterviewService.listInterviews(query));
    }

    @GetMapping("/{sessionId}")
    public Result<AdminInterviewService.AdminInterviewDetail> getInterviewDetail(@PathVariable String sessionId) {
        return Result.success(adminInterviewService.getInterviewDetail(sessionId));
    }

    @GetMapping("/{sessionId}/diagnosis")
    public Result<InterviewDiagnosisResponse> diagnoseInterview(@PathVariable String sessionId) {
        return Result.success(adminInterviewService.diagnoseInterview(sessionId));
    }

    @PatchMapping("/{sessionId}/cancel")
    public Result<Void> cancelInterview(@PathVariable String sessionId) {
        adminInterviewService.cancelInterview(sessionId);
        return Result.success();
    }
}
