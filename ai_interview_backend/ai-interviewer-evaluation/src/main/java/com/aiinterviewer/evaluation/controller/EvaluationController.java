package com.aiinterviewer.evaluation.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.evaluation.dto.EvaluationDTO;
import com.aiinterviewer.evaluation.dto.ScoreDTO;
import com.aiinterviewer.evaluation.dto.StatisticsDTO;
import com.aiinterviewer.evaluation.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评估控制器
 */
@Tag(name = "评估报告", description = "面试评估报告相关接口")
@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    private Long getCurrentUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalStateException("用户未登录");
        }
        return Long.parseLong(userIdStr);
    }

    /**
     * 生成评估报告
     */
    @Operation(summary = "生成评估报告")
    @PostMapping("/{sessionId}")
    public Result<EvaluationDTO> generateReport(@PathVariable("sessionId") String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        EvaluationDTO report = evaluationService.generateReport(sessionId, userId, null);
        return Result.success(report);
    }

    /**
     * 获取评估报告
     */
    @Operation(summary = "获取评估报告")
    @GetMapping("/{sessionId}")
    public Result<EvaluationDTO> getReport(@PathVariable("sessionId") String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        EvaluationDTO report = evaluationService.getReport(sessionId, userId);
        if (report == null) {
            return Result.success("评估报告尚未生成", null);
        }
        return Result.success(report);
    }

    /**
     * 获取评估列表
     */
    @Operation(summary = "获取评估列表")
    @GetMapping
    public Result<List<EvaluationDTO>> listReports(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<EvaluationDTO> reports = evaluationService.listReports(userId);
        return Result.success(reports);
    }

    /**
     * 获取评分详情
     */
    @Operation(summary = "获取评分详情")
    @GetMapping("/{sessionId}/scores")
    public Result<List<ScoreDTO>> getScores(@PathVariable("sessionId") String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<ScoreDTO> scores = evaluationService.getScores(sessionId, userId);
        return Result.success(scores);
    }

    /**
     * 获取统计数据
     */
    @Operation(summary = "获取统计数据")
    @GetMapping("/statistics")
    public Result<StatisticsDTO> getStatistics(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        StatisticsDTO stats = evaluationService.getStatistics(userId);
        return Result.success(stats);
    }
}
