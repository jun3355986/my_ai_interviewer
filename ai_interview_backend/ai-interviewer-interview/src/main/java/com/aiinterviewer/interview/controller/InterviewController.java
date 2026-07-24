package com.aiinterviewer.interview.controller;

import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.dto.LineageSummaryDTO;
import com.aiinterviewer.interview.dto.LineageTreeDTO;
import com.aiinterviewer.interview.dto.SessionDTO;
import com.aiinterviewer.interview.service.InterviewHistoryService;
import com.aiinterviewer.interview.service.InterviewService;
import com.aiinterviewer.interview.service.LineageTreeService;
import com.aiinterviewer.interview.service.SSEProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 面试控制器
 *
 * 核心接口：
 * - POST /chat: 统一对话接口（SSE流式响应）
 * - POST /{id}/resume: 恢复面试（SSE流式响应）
 * - GET /: 面试列表
 * - GET /incomplete: 未完成面试列表
 */
@Slf4j
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
@Tag(name = "面试管理", description = "面试会话管理和对话接口")
public class InterviewController {

    private final SSEProxyService sseProxyService;
    private final InterviewService interviewService;
    private final InterviewHistoryService interviewHistoryService;
    private final LineageTreeService lineageTreeService;

    /**
     * 统一对话接口 - SSE流式响应
     *
     * 这是核心接口，负责：
     * 1. 创建/获取面试会话
     * 2. 转发请求到Python后端
     * 3. 透传SSE流式响应
     * 4. 持久化评分等信息
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "统一对话接口", description = "SSE流式响应，支持创建新会话或继续已有会话")
    public Flux<ServerSentEvent<String>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username) {

        log.info("Chat request: userId={}, username={}, sessionId={}, message={}",
                userId, username, request.getSessionId(),
                request.getMessage().length() > 50 ?
                        request.getMessage().substring(0, 50) + "..." :
                        request.getMessage());

        return sseProxyService.proxyChat(request, userId, username)
                .timeout(Duration.ofMinutes(10))
                .doOnSubscribe(s -> log.debug("SSE stream started"))
                .doOnComplete(() -> log.debug("SSE stream completed"))
                .doOnError(e -> log.error("SSE stream error: {}", e.getMessage()));
    }

    /**
     * 恢复面试会话 - SSE流式响应
     */
    @PostMapping(value = "/{sessionId}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "恢复面试", description = "恢复未完成的面试会话，返回历史摘要和当前问题")
    public Flux<ServerSentEvent<String>> resumeInterview(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username) {

        log.info("Resume interview: userId={}, username={}, sessionId={}", userId, username, sessionId);

        return sseProxyService.proxyResume(sessionId, userId, username)
                .timeout(Duration.ofMinutes(5));
    }

    /**
     * 获取面试列表（分页）
     */
    @GetMapping
    @Operation(summary = "获取面试列表", description = "分页获取用户的面试历史")
    public Result<PageResult<SessionDTO>> listInterviews(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {

        return Result.success(interviewService.listSessions(userId, current, size));
    }

    /**
     * 获取未完成的面试列表
     */
    @GetMapping("/incomplete")
    @Operation(summary = "获取未完成面试", description = "获取用户所有未完成的面试会话")
    public Result<PageResult<SessionDTO>> listIncompleteSessions(
            @RequestHeader("X-User-Id") Long userId) {

        return Result.success(interviewService.listIncompleteSessions(userId));
    }

    /**
     * 获取按面试谱系聚合的真实历史列表。
     */
    @GetMapping("/lineages")
    @Operation(summary = "获取面试历史", description = "按面试谱系分页，包含分支数量、最佳成绩与当前可恢复分支")
    public Result<PageResult<LineageSummaryDTO>> listLineages(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sortBy", defaultValue = "time") String sortBy,
            @RequestParam(value = "status", defaultValue = "all") String status) {

        return Result.success(interviewHistoryService.listLineages(
                userId,
                current,
                size,
                keyword,
                sortBy,
                status));
    }

    @GetMapping("/lineages/{lineageId}/tree")
    @Operation(summary = "获取面试分支树", description = "返回用户拥有的谱系分支节点、评分和恢复状态")
    public Result<LineageTreeDTO> getLineageTree(
            @PathVariable("lineageId") String lineageId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(lineageTreeService.getTree(lineageId, userId));
    }

    /**
     * 获取面试详情
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "获取面试详情", description = "获取面试会话的详细信息")
    public Result<SessionDTO> getSession(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId,
            @RequestHeader("X-User-Id") Long userId) {

        return Result.success(interviewService.getSession(sessionId, userId));
    }

    /**
     * 获取指定面试分支的持久化回放，不触发AI恢复或生成。
     */
    @GetMapping("/branches/{branchId}/transcript")
    @Operation(summary = "获取分支回放", description = "组合祖先前缀与当前分支增量，只读取已持久化业务消息")
    public Result<BranchTranscriptDTO> getBranchTranscript(
            @Parameter(description = "分支会话ID") @PathVariable("branchId") String branchId,
            @RequestHeader("X-User-Id") Long userId) {

        return Result.success(interviewHistoryService.getBranchTranscript(branchId, userId));
    }

    /**
     * 取消面试
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "取消面试", description = "取消进行中的面试会话")
    public Result<Void> cancelInterview(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId,
            @RequestHeader("X-User-Id") Long userId) {

        interviewService.cancelSession(sessionId, userId);
        return Result.success();
    }
}
