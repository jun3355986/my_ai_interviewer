package com.aiinterviewer.admin.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminResumeServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminResumeService adminResumeService;

    @Test
    void resumeListSupportsUserIdAndParseStatusFilters() {
        seedResumes();

        AdminResumeService.AdminResumeQuery userQuery = new AdminResumeService.AdminResumeQuery();
        userQuery.setUserId(1L);
        PageResult<AdminResumeService.AdminResumeListItem> userResumes = adminResumeService.listResumes(userQuery);

        AdminResumeService.AdminResumeQuery statusQuery = new AdminResumeService.AdminResumeQuery();
        statusQuery.setParseStatus(3);
        PageResult<AdminResumeService.AdminResumeListItem> failedResumes = adminResumeService.listResumes(statusQuery);

        assertThat(userResumes.getTotal()).isEqualTo(2);
        assertThat(userResumes.getRecords())
                .extracting(AdminResumeService.AdminResumeListItem::getOriginalFileName)
                .containsExactly("Alice Java.pdf", "Alice AI.pdf");
        assertThat(failedResumes.getTotal()).isEqualTo(1);
        assertThat(failedResumes.getRecords().getFirst().getOriginalFileName()).isEqualTo("Bob Failed.pdf");
    }

    @Test
    void resumeDetailIncludesParsedContentRawTextAndVersions() {
        seedResumes();

        AdminResumeService.AdminResumeDetail detail = adminResumeService.getResumeDetail(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getParsedContent()).contains("\"skills\"");
        assertThat(detail.getRawText()).isEqualTo("Alice raw resume text");
        assertThat(detail.getVersions()).hasSize(2);
        assertThat(detail.getVersions())
                .extracting(AdminResumeService.AdminResumeVersionItem::getVersion)
                .containsExactly(2, 1);
        assertThat(detail.getVersions().getFirst().getParsedContent()).contains("\"Java\"");
    }

    private void seedResumes() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, phone, password_hash, nickname, status)
                VALUES
                    ('alice', 'alice@example.com', '18800000001', 'hash-a', 'Alice', 1),
                    ('bob', 'bob@example.com', '18800000002', 'hash-b', 'Bob', 1)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_resume
                    (user_id, file_name, original_file_name, file_path, file_size, content_type,
                     parsed_content, raw_text, parse_status, parse_error, is_default, version_count,
                     parsed_at, created_at, updated_at)
                VALUES
                    (1, 'alice-java.pdf', 'Alice Java.pdf', '/resume/alice-java.pdf', 1024, 'application/pdf',
                     CAST('{"skills":["Java","Spring"]}' AS jsonb), 'Alice raw resume text', 2, NULL, TRUE, 2,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'),
                    (1, 'alice-ai.pdf', 'Alice AI.pdf', '/resume/alice-ai.pdf', 2048, 'application/pdf',
                     CAST('{"skills":["AI"]}' AS jsonb), 'Alice AI raw text', 1, NULL, FALSE, 1,
                     NULL, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP),
                    (2, 'bob-failed.pdf', 'Bob Failed.pdf', '/resume/bob-failed.pdf', 4096, 'application/pdf',
                     NULL, 'Bob raw text', 3, 'parse failed', FALSE, 1,
                     NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_resume_version
                    (resume_id, version, file_path, file_name, file_size, parsed_content,
                     operation_type, operator_id, remark, created_at)
                VALUES
                    (1, 1, '/resume/alice-java-v1.pdf', 'alice-java-v1.pdf', 900,
                     CAST('{"skills":["Java"]}' AS jsonb), 'UPLOAD', 1, 'initial', CURRENT_TIMESTAMP - INTERVAL '2 days'),
                    (1, 2, '/resume/alice-java-v2.pdf', 'alice-java-v2.pdf', 1024,
                     CAST('{"skills":["Java","Spring"]}' AS jsonb), 'UPDATE', 1, 'updated', CURRENT_TIMESTAMP - INTERVAL '1 day')
                """);
    }
}
