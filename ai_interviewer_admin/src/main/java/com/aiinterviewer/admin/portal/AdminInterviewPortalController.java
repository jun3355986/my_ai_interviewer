package com.aiinterviewer.admin.portal;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试门户 — 管理端发起与回放真实面试的统一入口。
 *
 * 以登录管理员的 userId 注入 X-User-Id 后透传 interview 微服务：
 * 发起（start-attempts）、逐轮提交（turn-attempts，乐观锁参数由前端透传）、
 * 分叉（fork-attempts）与谱系/分支/转录查询。响应为 interview 侧原始
 * 统一 Result JSON，非 2xx（如 409 轮次冲突）保持原状态码返回。
 */
@RestController
@RequestMapping("/admin/interview-portal")
@RequiredArgsConstructor
public class AdminInterviewPortalController {

    private final InterviewServiceClient interviewServiceClient;
    private final PortalIdentityResolver identityResolver;

    @GetMapping("/lineages")
    public ResponseEntity<String> lineages(
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest request) {
        return forwardGet(adminUserId, "/interviews/lineages", request);
    }

    @GetMapping("/lineages/{lineageId}/tree")
    public ResponseEntity<String> lineageTree(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String lineageId) {
        return forwardGet(adminUserId, "/interviews/lineages/" + lineageId + "/tree", null);
    }

    @GetMapping("/branches/{branchId}/transcript")
    public ResponseEntity<String> branchTranscript(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String branchId) {
        return forwardGet(adminUserId, "/interviews/branches/" + branchId + "/transcript", null);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<String> sessionDetail(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String sessionId) {
        return forwardGet(adminUserId, "/interviews/" + sessionId, null);
    }

    @GetMapping("/turn-attempts/{turnId}")
    public ResponseEntity<String> turnAttempt(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String turnId) {
        return forwardGet(adminUserId, "/interviews/turn-attempts/" + turnId, null);
    }

    @PostMapping("/start-attempts")
    @AdminAudit(module = "INTERVIEW_PORTAL", operation = "START_ATTEMPT", targetType = "INTERVIEW")
    public ResponseEntity<String> startAttempt(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody String body) {
        return forwardPost(adminUserId, "/interviews/start-attempts", body);
    }

    @PostMapping("/branches/{branchId}/turn-attempts")
    @AdminAudit(module = "INTERVIEW_PORTAL", operation = "TURN_ATTEMPT", targetType = "INTERVIEW_BRANCH",
            targetIdParam = "branchId")
    public ResponseEntity<String> turnAttempt(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String branchId,
            @RequestBody String body) {
        return forwardPost(adminUserId, "/interviews/branches/" + branchId + "/turn-attempts", body);
    }

    @PostMapping("/turn-attempts/{turnId}/retry")
    @AdminAudit(module = "INTERVIEW_PORTAL", operation = "RETRY_ATTEMPT", targetType = "TURN_ATTEMPT",
            targetIdParam = "turnId")
    public ResponseEntity<String> retryAttempt(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String turnId,
            @RequestBody(required = false) String body) {
        return forwardPost(adminUserId, "/interviews/turn-attempts/" + turnId + "/retry", body);
    }

    @PostMapping("/branches/{branchId}/fork-attempts")
    @AdminAudit(module = "INTERVIEW_PORTAL", operation = "FORK_ATTEMPT", targetType = "INTERVIEW_BRANCH",
            targetIdParam = "branchId")
    public ResponseEntity<String> forkAttempt(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String branchId,
            @RequestBody String body) {
        return forwardPost(adminUserId, "/interviews/branches/" + branchId + "/fork-attempts", body);
    }

    private ResponseEntity<String> forwardGet(Long adminUserId, String path, HttpServletRequest request) {
        String query = request == null ? null : request.getQueryString();
        String target = query == null || query.isBlank() ? path : path + "?" + query;
        PortalIdentityResolver.PortalIdentity identity = identityResolver.requireIdentity(adminUserId);
        InterviewServiceClient.UpstreamResponse upstream =
                interviewServiceClient.get(target, identity.id(), identity.username());
        return ResponseEntity.status(upstream.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.body());
    }

    private ResponseEntity<String> forwardPost(Long adminUserId, String path, String body) {
        PortalIdentityResolver.PortalIdentity identity = identityResolver.requireIdentity(adminUserId);
        InterviewServiceClient.UpstreamResponse upstream =
                interviewServiceClient.post(path, identity.id(), identity.username(), body);
        return ResponseEntity.status(upstream.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.body());
    }
}
