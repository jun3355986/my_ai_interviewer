package com.aiinterviewer.admin.evaluation;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/evaluations")
@RequiredArgsConstructor
public class AdminEvaluationController {

    private final AdminEvaluationService adminEvaluationService;

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
}
