package com.aiinterviewer.interview.service;

import cn.hutool.core.util.StrUtil;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.MockCandidateAnswerDTO;
import com.aiinterviewer.interview.dto.MockCandidateAnswerRequest;
import com.aiinterviewer.interview.repository.StartAttemptRepository;
import com.aiinterviewer.interview.repository.StartAttemptRepository.JobContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 模拟面试候选人生成代理
 *
 * 校验简历归属后把问题与上下文转发给 Python `/interview/mock/candidate-answer`；
 * 无状态、不落库，面试推进仍由 durable-turn 管线负责。
 *
 * 简历文本优先 raw_text，缺失时回退 parsed_content 序列化文本
 * （当前上传/解析链路只写 parsed_content，raw_text 历史上始终为空）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockCandidateProxyService {

    private static final Set<String> SUPPORTED_QUESTION_TYPES =
            Set.of("self_introduction", "project", "technical");

    private static final Duration PYTHON_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;
    private final StartAttemptRepository startRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${python.ai.base-url:${python-ai.base-url:http://localhost:8000}}")
    private String pythonBaseUrl;

    public MockCandidateAnswerDTO generate(MockCandidateAnswerRequest request, Long userId) {
        validate(request);
        ResumeText resume = findOwnedResumeText(request.getResumeId(), userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESUME_NOT_FOUND,
                        "简历不存在或无权访问"));
        if (!StringUtils.hasText(resume.text())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历缺少可用的文本内容，无法生成候选人回答");
        }
        String jobRequirements = startRepository.findActiveJob(request.getJobId())
                .map(JobContext::requirements)
                .orElse(null);

        return callPython(request, resume, jobRequirements);
    }

    private void validate(MockCandidateAnswerRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getQuestion())
                || !StringUtils.hasText(request.getQuestionType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模拟面试参数无效");
        }
        if (!SUPPORTED_QUESTION_TYPES.contains(request.getQuestionType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的问题类型: " + request.getQuestionType());
        }
    }

    private Optional<ResumeText> findOwnedResumeText(Long resumeId, Long userId) {
        if (resumeId == null) {
            return Optional.empty();
        }
        // 与 StartAttemptRepository.findOwnedResume 相同的回退链：
        // raw_text（新链路会写）→ otherInfo（解析保留的原文）→ 整个 parsed_content。
        return jdbcTemplate.query("""
                        SELECT COALESCE(NULLIF(raw_text, ''), NULLIF(parsed_content->>'otherInfo', ''),
                                        parsed_content::text) AS resume_text,
                               parsed_content ->> 'name' AS candidate_name
                        FROM t_resume
                        WHERE id = ? AND user_id = ?
                        """,
                        (rs, rowNumber) -> new ResumeText(
                                rs.getString("resume_text"),
                                rs.getString("candidate_name")),
                        resumeId,
                        userId)
                .stream()
                .findFirst();
    }

    private MockCandidateAnswerDTO callPython(
            MockCandidateAnswerRequest request,
            ResumeText resume,
            String jobRequirements) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", request.getQuestion());
        payload.put("question_type", request.getQuestionType());
        payload.put("resume_content", resume.text());
        payload.put("job_requirements", jobRequirements);
        payload.put("candidate_name", resume.candidateName());
        if (request.getRecentHistory() != null && !request.getRecentHistory().isEmpty()) {
            payload.put("recent_history", request.getRecentHistory().stream()
                    .map(item -> Map.of(
                            "question", StrUtil.nullToEmpty(item.getQuestion()),
                            "answer", StrUtil.nullToEmpty(item.getAnswer())))
                    .toList());
        }
        payload.put("java_session_id", request.getJavaSessionId());
        payload.put("request_id", request.getRequestId());

        try {
            String body = webClient.post()
                    .uri(pythonBaseUrl + "/interview/mock/candidate-answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(PYTHON_TIMEOUT)
                    .block();
            JsonNode node = objectMapper.readTree(body);
            String answer = node.path("answer").asText(null);
            if (!StringUtils.hasText(answer)) {
                throw new IllegalStateException("候选人生成响应缺少 answer 字段");
            }
            return new MockCandidateAnswerDTO(answer);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Mock candidate answer proxy failed: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 候选人生成暂时不可用，请稍后重试");
        }
    }

    private record ResumeText(String text, String candidateName) {
    }
}
