package com.aiinterviewer.admin.evaluation;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.portal.EvaluationServiceClient;
import com.aiinterviewer.admin.portal.PortalIdentityResolver;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/evaluations")
@RequiredArgsConstructor
public class AdminEvaluationController {

    private final AdminEvaluationService adminEvaluationService;
    private final EvaluationServiceClient evaluationServiceClient;
    private final PortalIdentityResolver identityResolver;

    @GetMapping
    public Result<PageResult<AdminEvaluationService.AdminEvaluationListItem>> listEvaluations(
            @RequestParam(required = false) String recommendation,
            @RequestParam(required = false) Integer minOverallScore,
            @RequestParam(required = false) Integer maxOverallScore,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AdminEvaluationService.AdminEvaluationQuery query = new AdminEvaluationService.AdminEvaluationQuery();
        query.setRecommendation(recommendation);
        query.setMinOverallScore(minOverallScore);
        query.setMaxOverallScore(maxOverallScore);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(adminEvaluationService.listEvaluations(query));
    }

    @GetMapping("/{evaluationId}")
    public Result<AdminEvaluationService.AdminEvaluationListItem> evaluationDetail(
            @PathVariable Long evaluationId) {
        AdminEvaluationService.AdminEvaluationListItem item =
                adminEvaluationService.findEvaluationById(evaluationId);
        if (item == null) {
            throw new AdminBusinessException(404, "评估报告不存在");
        }
        return Result.success(item);
    }

    @GetMapping("/by-session/{sessionId}")
    public Result<AdminEvaluationService.AdminEvaluationListItem> evaluationBySession(
            @PathVariable String sessionId) {
        AdminEvaluationService.AdminEvaluationListItem item =
                adminEvaluationService.findEvaluationBySessionId(sessionId);
        if (item == null) {
            throw new AdminBusinessException(404, "该面试尚未生成评估报告");
        }
        return Result.success(item);
    }

    /**
     * 结束面试后生成（或重建）评估报告：代理 evaluation 服务，
     * 注入当前管理员 userId 以满足评估侧的分支归属校验。
     */
    @PostMapping("/{sessionId}/generate")
    @AdminAudit(module = "EVALUATION_PORTAL", operation = "GENERATE_REPORT", targetType = "INTERVIEW_SESSION",
            targetIdParam = "sessionId")
    public ResponseEntity<String> generateReport(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String sessionId) {
        PortalIdentityResolver.PortalIdentity identity = identityResolver.requireIdentity(adminUserId);
        EvaluationServiceClient.ExchangeResponse upstream =
                evaluationServiceClient.generateReport(sessionId, identity.id());
        return ResponseEntity.status(upstream.status())
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(upstream.body());
    }
}
