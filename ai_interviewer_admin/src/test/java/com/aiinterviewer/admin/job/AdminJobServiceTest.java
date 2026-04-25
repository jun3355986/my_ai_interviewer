package com.aiinterviewer.admin.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminJobServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminJobService adminJobService;

    @Test
    void jobListSupportsTitleCompanyStatusAndSkillFilters() {
        seedJobs();

        AdminJobService.AdminJobQuery titleQuery = new AdminJobService.AdminJobQuery();
        titleQuery.setTitle("java");
        PageResult<AdminJobService.AdminJobListItem> javaJobs = adminJobService.listJobs(titleQuery);

        AdminJobService.AdminJobQuery companyQuery = new AdminJobService.AdminJobQuery();
        companyQuery.setCompany("Beta");
        PageResult<AdminJobService.AdminJobListItem> betaJobs = adminJobService.listJobs(companyQuery);

        AdminJobService.AdminJobQuery statusQuery = new AdminJobService.AdminJobQuery();
        statusQuery.setStatus(0);
        PageResult<AdminJobService.AdminJobListItem> closedJobs = adminJobService.listJobs(statusQuery);

        AdminJobService.AdminJobQuery skillQuery = new AdminJobService.AdminJobQuery();
        skillQuery.setSkill("Spring");
        PageResult<AdminJobService.AdminJobListItem> springJobs = adminJobService.listJobs(skillQuery);

        assertThat(javaJobs.getTotal()).isEqualTo(1);
        assertThat(javaJobs.getRecords().getFirst().getTitle()).isEqualTo("Senior Java Engineer");
        assertThat(betaJobs.getTotal()).isEqualTo(1);
        assertThat(betaJobs.getRecords().getFirst().getCompany()).isEqualTo("Beta AI");
        assertThat(closedJobs.getTotal()).isEqualTo(1);
        assertThat(closedJobs.getRecords().getFirst().getTitle()).isEqualTo("Closed QA Engineer");
        assertThat(springJobs.getTotal()).isEqualTo(1);
        assertThat(springJobs.getRecords().getFirst().getSkills()).contains("Java", "Spring");
    }

    @Test
    void skillFilterMatchesExactJsonArrayElementIgnoringCase() {
        seedJobs();
        jdbcTemplate.update(
                """
                INSERT INTO t_job
                    (title, company, department, location, job_type, experience_required,
                     education_required, salary_min, salary_max, description, requirements,
                     skills, status, created_by, created_at, updated_at)
                VALUES
                    ('Frontend JavaScript Engineer', 'Gamma UI', 'R&D', 'Shenzhen', 'full-time', '3 years',
                     'Bachelor', 20000, 30000, 'Build frontend', 'JavaScript and React',
                     CAST('["JavaScript","React"]' AS jsonb), 1, 1,
                     CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """);

        AdminJobService.AdminJobQuery skillQuery = new AdminJobService.AdminJobQuery();
        skillQuery.setSkill("java");
        PageResult<AdminJobService.AdminJobListItem> javaJobs = adminJobService.listJobs(skillQuery);

        assertThat(javaJobs.getTotal()).isEqualTo(1);
        assertThat(javaJobs.getRecords())
                .extracting(AdminJobService.AdminJobListItem::getTitle)
                .containsExactly("Senior Java Engineer");
    }

    @Test
    void adminCanCreateAndUpdateJobRecords() {
        seedCreator();
        AdminJobService.AdminJobUpsertRequest request = jobRequest("Backend Engineer", "Acme");
        request.setSkills(List.of("Java", "PostgreSQL"));

        Long jobId = adminJobService.createJob(request);

        AdminJobService.AdminJobDetail created = adminJobService.getJobDetail(jobId);
        assertThat(created.getTitle()).isEqualTo("Backend Engineer");
        assertThat(created.getSkills()).containsExactly("Java", "PostgreSQL");

        AdminJobService.AdminJobUpsertRequest update = jobRequest("Platform Engineer", "Acme Cloud");
        update.setLocation("Shenzhen");
        update.setSkills(List.of("Kubernetes", "Java"));
        adminJobService.updateJob(jobId, update);

        AdminJobService.AdminJobDetail updated = adminJobService.getJobDetail(jobId);
        assertThat(updated.getTitle()).isEqualTo("Platform Engineer");
        assertThat(updated.getCompany()).isEqualTo("Acme Cloud");
        assertThat(updated.getLocation()).isEqualTo("Shenzhen");
        assertThat(updated.getSkills()).containsExactly("Kubernetes", "Java");
    }

    @Test
    void updateClosedJobWithoutStatusKeepsItClosed() {
        seedCreator();
        AdminJobService.AdminJobUpsertRequest request = jobRequest("Backend Engineer", "Acme");
        Long jobId = adminJobService.createJob(request);
        adminJobService.closeJob(jobId);

        AdminJobService.AdminJobUpsertRequest update = jobRequest("Platform Engineer", "Acme Cloud");
        update.setStatus(null);
        adminJobService.updateJob(jobId, update);

        AdminJobService.AdminJobDetail updated = adminJobService.getJobDetail(jobId);
        assertThat(updated.getStatus()).isZero();
        assertThat(updated.getTitle()).isEqualTo("Platform Engineer");
    }

    @Test
    void createJobAuditTargetIdUsesCreatedJobId() {
        seedCreator();
        AdminJobService.AdminJobUpsertRequest request = jobRequest("Backend Engineer", "Acme");

        Long jobId = adminJobService.createJob(request);

        AdminOperationLog log = jdbcTemplate.queryForObject(
                """
                SELECT target_id
                FROM t_admin_operation_log
                WHERE module = 'JOB'
                  AND operation = 'CREATE'
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> {
                    AdminOperationLog operationLog = new AdminOperationLog();
                    operationLog.setTargetId(rs.getString("target_id"));
                    return operationLog;
                });
        assertThat(log).isNotNull();
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(jobId));
    }

    @Test
    void adminCanCloseAndReopenJob() {
        seedJobs();

        adminJobService.closeJob(1L);
        assertThat(adminJobService.getJobDetail(1L).getStatus()).isZero();

        adminJobService.reopenJob(1L);
        assertThat(adminJobService.getJobDetail(1L).getStatus()).isEqualTo(1);
    }

    @Test
    void adminCanConfigureQuestionTypesAndCountsForJob() {
        seedJobs();
        AdminJobService.JobQuestionConfigRequest request = new AdminJobService.JobQuestionConfigRequest();
        request.setQuestions(List.of(
                question("TECHNICAL", 8, 1),
                question("PROJECT", 4, 2),
                question("BEHAVIORAL", 2, 3)));

        adminJobService.configureQuestions(1L, request);

        AdminJobService.AdminJobDetail detail = adminJobService.getJobDetail(1L);
        assertThat(detail.getQuestions())
                .extracting(AdminJobService.JobQuestionConfigItem::getQuestionType)
                .containsExactly("TECHNICAL", "PROJECT", "BEHAVIORAL");
        assertThat(detail.getQuestions())
                .extracting(AdminJobService.JobQuestionConfigItem::getQuestionCount)
                .containsExactly(8, 4, 2);
    }

    @Test
    void duplicateQuestionTypeConfigIsRejectedIgnoringCaseAndWhitespace() {
        seedJobs();
        AdminJobService.JobQuestionConfigRequest request = new AdminJobService.JobQuestionConfigRequest();
        request.setQuestions(List.of(
                question("TECHNICAL", 8, 1),
                question(" technical ", 4, 2)));

        assertThatThrownBy(() -> adminJobService.configureQuestions(1L, request))
                .hasMessageContaining("问题类型不能重复");
    }

    private void seedJobs() {
        seedCreator();
        jdbcTemplate.update(
                """
                INSERT INTO t_job
                    (title, company, department, location, job_type, experience_required,
                     education_required, salary_min, salary_max, description, requirements,
                     skills, status, created_by, created_at, updated_at)
                VALUES
                    ('Senior Java Engineer', 'Acme', 'R&D', 'Shenzhen', 'full-time', '5 years',
                     'Bachelor', 25000, 35000, 'Build backend', 'Java and Spring',
                     CAST('["Java","Spring"]' AS jsonb), 1, 1,
                     CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
                    ('AI Product Manager', 'Beta AI', 'Product', 'Guangzhou', 'full-time', '3 years',
                     'Bachelor', 22000, 30000, 'AI product', 'LLM product',
                     CAST('["AI","Product"]' AS jsonb), 1, 1,
                     CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
                    ('Closed QA Engineer', 'Acme', 'QA', 'Remote', 'contract', '2 years',
                     'College', 12000, 18000, 'Test systems', 'Automation',
                     CAST('["Testing"]' AS jsonb), 0, 1,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    private void seedCreator() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, password_hash, status)
                VALUES ('admin', 'admin@example.com', 'hash', 1)
                """);
    }

    private AdminJobService.AdminJobUpsertRequest jobRequest(String title, String company) {
        AdminJobService.AdminJobUpsertRequest request = new AdminJobService.AdminJobUpsertRequest();
        request.setTitle(title);
        request.setCompany(company);
        request.setDepartment("Engineering");
        request.setLocation("Guangzhou");
        request.setJobType("full-time");
        request.setExperienceRequired("3 years");
        request.setEducationRequired("Bachelor");
        request.setSalaryMin(new BigDecimal("18000"));
        request.setSalaryMax(new BigDecimal("28000"));
        request.setDescription("Build interview platform");
        request.setRequirements("Java, Spring Boot");
        request.setStatus(1);
        request.setCreatedBy(1L);
        return request;
    }

    private AdminJobService.JobQuestionConfigItem question(String questionType, int questionCount, int priority) {
        AdminJobService.JobQuestionConfigItem item = new AdminJobService.JobQuestionConfigItem();
        item.setQuestionType(questionType);
        item.setQuestionCount(questionCount);
        item.setPriority(priority);
        return item;
    }
}
