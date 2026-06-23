package com.aiinterviewer.interview.controller;

import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.dto.SessionDTO;
import com.aiinterviewer.interview.service.InterviewService;
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
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username) {

        // 如果没有用户ID，使用默认值（开发测试用）
        if (userId == null) {
            userId = 1L;
            log.warn("No X-User-Id header, using default userId: {}", userId);
        }

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
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            userId = 1L;
        }

        log.info("Resume interview: userId={}, sessionId={}", userId, sessionId);

        return sseProxyService.proxyResume(sessionId, userId)
                .timeout(Duration.ofMinutes(5));
    }

    /**
     * 获取面试列表（分页）
     */
    @GetMapping
    @Operation(summary = "获取面试列表", description = "分页获取用户的面试历史")
    public Result<PageResult<SessionDTO>> listInterviews(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        if (userId == null) {
            userId = 1L;
        }

        return Result.success(interviewService.listSessions(userId, current, size));
    }

    /**
     * 获取未完成的面试列表
     */
    @GetMapping("/incomplete")
    @Operation(summary = "获取未完成面试", description = "获取用户所有未完成的面试会话")
    public Result<PageResult<SessionDTO>> listIncompleteSessions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            userId = 1L;
        }

        return Result.success(interviewService.listIncompleteSessions(userId));
    }

    /**
     * 获取面试详情
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "获取面试详情", description = "获取面试会话的详细信息")
    public Result<SessionDTO> getSession(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            userId = 1L;
        }

        return Result.success(interviewService.getSession(sessionId, userId));
    }

    /**
     * 取消面试
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "取消面试", description = "取消进行中的面试会话")
    public Result<Void> cancelInterview(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            userId = 1L;
        }

        interviewService.cancelSession(sessionId, userId);
        return Result.success();
    }
}
