package com.aiinterviewer.admin.job;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.job.mapper.AdminJobMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminJobService {

    private static final int OPEN_STATUS = 1;
    private static final int CLOSED_STATUS = 0;
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;

    private final AdminJobMapper adminJobMapper;
    private final ObjectMapper objectMapper;

    public PageResult<AdminJobListItem> listJobs(AdminJobQuery query) {
        AdminJobQuery safeQuery = query == null ? new AdminJobQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = adminJobMapper.countJobs(safeQuery);
        List<AdminJobListItem> records = adminJobMapper.selectJobs(safeQuery, size, safeOffset(current, size));
        records.forEach(this::hydrateSkills);
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public AdminJobDetail getJobDetail(Long jobId) {
        ensureJobId(jobId);
        AdminJobDetail detail = adminJobMapper.selectJobDetail(jobId);
        if (detail == null) {
            throw new AdminBusinessException(404, "岗位不存在");
        }
        hydrateSkills(detail);
        detail.setQuestions(adminJobMapper.selectJobQuestions(jobId));
        return detail;
    }

    @Transactional
    @AdminAudit(module = "JOB", operation = "CREATE", targetType = "JOB", targetIdParam = "arg0")
    public Long createJob(AdminJobUpsertRequest request) {
        validateJobRequest(request);
        request.setSkillsJson(toJson(request.getSkills()));
        if (request.getStatus() == null) {
            request.setStatus(OPEN_STATUS);
        }
        int inserted = adminJobMapper.insertJob(request);
        if (inserted == 0 || request.getId() == null) {
            throw new AdminBusinessException(500, "岗位创建失败");
        }
        return request.getId();
    }

    @Transactional
    @AdminAudit(module = "JOB", operation = "UPDATE", targetType = "JOB", targetIdParam = "jobId")
    public void updateJob(Long jobId, AdminJobUpsertRequest request) {
        ensureJobExists(jobId);
        validateJobRequest(request);
        request.setId(jobId);
        request.setSkillsJson(toJson(request.getSkills()));
        if (request.getStatus() == null) {
            request.setStatus(OPEN_STATUS);
        }
        int updated = adminJobMapper.updateJob(request);
        if (updated == 0) {
            throw new AdminBusinessException(500, "岗位更新失败");
        }
    }

    @Transactional
    @AdminAudit(module = "JOB", operation = "CLOSE", targetType = "JOB", targetIdParam = "jobId")
    public void closeJob(Long jobId) {
        ensureJobExists(jobId);
        int updated = adminJobMapper.updateJobStatus(jobId, CLOSED_STATUS);
        if (updated == 0) {
            throw new AdminBusinessException(500, "岗位关闭失败");
        }
    }

    @Transactional
    @AdminAudit(module = "JOB", operation = "REOPEN", targetType = "JOB", targetIdParam = "jobId")
    public void reopenJob(Long jobId) {
        ensureJobExists(jobId);
        int updated = adminJobMapper.updateJobStatus(jobId, OPEN_STATUS);
        if (updated == 0) {
            throw new AdminBusinessException(500, "岗位重开失败");
        }
    }

    @Transactional
    @AdminAudit(module = "JOB", operation = "CONFIGURE_QUESTIONS", targetType = "JOB", targetIdParam = "jobId")
    public void configureQuestions(Long jobId, JobQuestionConfigRequest request) {
        ensureJobExists(jobId);
        List<JobQuestionConfigItem> questions = request == null ? List.of() : request.getQuestions();
        validateQuestionConfig(questions);
        adminJobMapper.deleteJobQuestions(jobId);
        if (!questions.isEmpty()) {
            adminJobMapper.insertJobQuestions(jobId, questions);
        }
    }

    private void ensureJobId(Long jobId) {
        if (jobId == null) {
            throw new AdminBusinessException(400, "岗位ID不能为空");
        }
    }

    private void ensureJobExists(Long jobId) {
        ensureJobId(jobId);
        Integer count = adminJobMapper.countExistingJob(jobId);
        if (count == null || count == 0) {
            throw new AdminBusinessException(404, "岗位不存在");
        }
    }

    private void validateJobRequest(AdminJobUpsertRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "岗位参数不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new AdminBusinessException(400, "岗位名称不能为空");
        }
        if (request.getSalaryMin() != null && request.getSalaryMax() != null
                && request.getSalaryMin().compareTo(request.getSalaryMax()) > 0) {
            throw new AdminBusinessException(400, "最低薪资不能大于最高薪资");
        }
        if (request.getStatus() != null && request.getStatus() != OPEN_STATUS && request.getStatus() != CLOSED_STATUS) {
            throw new AdminBusinessException(400, "岗位状态不合法");
        }
    }

    private void validateQuestionConfig(List<JobQuestionConfigItem> questions) {
        if (questions == null) {
            return;
        }
        for (JobQuestionConfigItem question : questions) {
            if (question == null || !StringUtils.hasText(question.getQuestionType())) {
                throw new AdminBusinessException(400, "问题类型不能为空");
            }
            if (question.getQuestionCount() == null || question.getQuestionCount() < 1) {
                throw new AdminBusinessException(400, "问题数量必须大于0");
            }
            if (question.getPriority() == null) {
                question.setPriority(0);
            }
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new AdminBusinessException(400, "技能标签格式不合法", ex);
        }
    }

    private void hydrateSkills(JobSkillCarrier carrier) {
        carrier.setSkills(readStringList(carrier.getSkillsJson()));
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values == null ? List.of() : values;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    interface JobSkillCarrier {

        String getSkillsJson();

        void setSkills(List<String> skills);
    }

    @Data
    public static class AdminJobQuery {

        private String title;
        private String company;
        private Integer status;
        private String skill;
        private Long current = DEFAULT_CURRENT;
        private Long size = DEFAULT_SIZE;

        long normalizedCurrent() {
            if (current == null || current < 1) {
                return DEFAULT_CURRENT;
            }
            return Math.min(current, MAX_CURRENT);
        }

        long normalizedSize() {
            if (size == null || size < 1) {
                return DEFAULT_SIZE;
            }
            return Math.min(size, MAX_SIZE);
        }
    }

    @Data
    public static class AdminJobListItem implements JobSkillCarrier {

        private Long id;
        private String title;
        private String company;
        private String department;
        private String location;
        private String jobType;
        private String skillsJson;
        private List<String> skills = List.of();
        private Integer status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AdminJobDetail implements JobSkillCarrier {

        private Long id;
        private String title;
        private String company;
        private String department;
        private String location;
        private String jobType;
        private String experienceRequired;
        private String educationRequired;
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String description;
        private String requirements;
        private String skillsJson;
        private List<String> skills = List.of();
        private Integer status;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<JobQuestionConfigItem> questions = new ArrayList<>();
    }

    @Data
    public static class AdminJobUpsertRequest {

        private Long id;
        private String title;
        private String company;
        private String department;
        private String location;
        private String jobType;
        private String experienceRequired;
        private String educationRequired;
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String description;
        private String requirements;
        private List<String> skills = List.of();
        private String skillsJson;
        private Integer status;
        private Long createdBy;
    }

    @Data
    public static class JobQuestionConfigRequest {

        private List<JobQuestionConfigItem> questions = List.of();
    }

    @Data
    public static class JobQuestionConfigItem {

        private Long id;
        private Long jobId;
        private String questionType;
        private Integer questionCount;
        private Integer priority;
        private LocalDateTime createdAt;
    }
}
