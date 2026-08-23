package com.aiinterviewer.admin.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端上传简历 → Python 结构化解析 → 落 t_resume（共享库直写，与
 * interview 服务 StartAttemptRepository 读 t_resume 的既有模式一致）。
 *
 * 落库的关键列：raw_text（作为后续面试的 resume_content）与
 * parsed_content->>'name'（候选人姓名来源）。
 */
@Service
@RequiredArgsConstructor
public class AdminResumePortalService {

    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final PythonPortalClient pythonPortalClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResumeUploadResponse parseAndSave(Long userId, MultipartFile file) {
        validate(file);
        Map<String, Object> parsed = pythonPortalClient.parseResume(file);
        String rawText = resolveRawText(parsed);
        String parsedJson = writeJson(parsed);
        String filename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "resume.pdf";

        jdbcTemplate.update(
                """
                INSERT INTO t_resume (
                    user_id, file_name, original_file_name, file_path, file_size, content_type,
                    parsed_content, raw_text, parse_status, is_default, version_count,
                    created_at, updated_at, parsed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 2, FALSE, 1,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                filename,
                filename,
                "admin-portal/" + filename,
                file.getSize(),
                StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/pdf",
                parsedJson,
                rawText);

        ResumeUploadResponse response = new ResumeUploadResponse();
        response.setResumeId(lastInsertedResumeId(userId));
        response.setFilename(filename);
        response.setName(textValue(parsed.get("name")));
        response.setJobIntent(textValue(parsed.get("jobIntent")));
        response.setWorkYears(textValue(parsed.get("workYears")));
        response.setEducation(textValue(parsed.get("education")));
        response.setUniversity(textValue(parsed.get("university")));
        response.setMajor(textValue(parsed.get("major")));
        response.setSkillCount(countList(parsed.get("skills")));
        response.setProjectCount(countList(parsed.get("projectExperience")));
        response.setPreview(rawText.length() > 200 ? rawText.substring(0, 200) + "…" : rawText);
        return response;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 PDF 简历文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".pdf") && !"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("仅支持 PDF 格式简历");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("简历文件不能超过 10 MB");
        }
    }

    private String resolveRawText(Map<String, Object> parsed) {
        Object otherInfo = parsed.get("otherInfo");
        if (otherInfo != null && StringUtils.hasText(String.valueOf(otherInfo))) {
            return String.valueOf(otherInfo);
        }
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "姓名", parsed.get("name"));
        appendSection(builder, "求职意向", parsed.get("jobIntent"));
        appendSection(builder, "教育", parsed.get("education"));
        appendSection(builder, "院校", parsed.get("university"));
        appendSection(builder, "专业", parsed.get("major"));
        appendSection(builder, "技能", parsed.get("skills"));
        appendSection(builder, "工作经历", parsed.get("workExperience"));
        appendSection(builder, "项目经历", parsed.get("projectExperience"));
        appendSection(builder, "自我评价", parsed.get("selfEvaluation"));
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (text.isBlank() || "[]".equals(text) || "{}".equals(text)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(text);
    }

    private String writeJson(Map<String, Object> parsed) {
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ex) {
            throw new IllegalStateException("简历解析结果序列化失败", ex);
        }
    }

    private Long lastInsertedResumeId(Long userId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM t_resume WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                userId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("简历保存后查询失败");
        }
        return ids.getFirst();
    }

    private static String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int countList(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    @Data
    public static class ResumeUploadResponse {
        private Long resumeId;
        private String filename;
        private String name;
        private String jobIntent;
        private String workYears;
        private String education;
        private String university;
        private String major;
        private Integer skillCount;
        private Integer projectCount;
        private String preview;
    }
}
