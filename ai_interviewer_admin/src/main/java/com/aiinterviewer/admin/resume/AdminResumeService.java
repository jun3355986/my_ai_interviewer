package com.aiinterviewer.admin.resume;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.resume.mapper.AdminResumeMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminResumeService {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;

    private final AdminResumeMapper adminResumeMapper;

    public PageResult<AdminResumeListItem> listResumes(AdminResumeQuery query) {
        AdminResumeQuery safeQuery = query == null ? new AdminResumeQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = adminResumeMapper.countResumes(safeQuery);
        List<AdminResumeListItem> records = adminResumeMapper.selectResumes(safeQuery, size, safeOffset(current, size));
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public AdminResumeDetail getResumeDetail(Long resumeId) {
        if (resumeId == null) {
            throw new AdminBusinessException(400, "简历ID不能为空");
        }
        AdminResumeDetail detail = adminResumeMapper.selectResumeDetail(resumeId);
        if (detail == null) {
            throw new AdminBusinessException(404, "简历不存在");
        }
        detail.setVersions(adminResumeMapper.selectResumeVersions(resumeId));
        return detail;
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    @Data
    public static class AdminResumeQuery {

        private Long userId;
        private Integer parseStatus;
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
    public static class AdminResumeListItem {

        private Long id;
        private Long userId;
        private String username;
        private String originalFileName;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private Integer parseStatus;
        private String parseError;
        private Boolean defaultResume;
        private Integer versionCount;
        private LocalDateTime parsedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AdminResumeDetail {

        private Long id;
        private Long userId;
        private String username;
        private String originalFileName;
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String contentType;
        private String parsedContent;
        private String rawText;
        private Integer parseStatus;
        private String parseError;
        private Boolean defaultResume;
        private Integer versionCount;
        private LocalDateTime parsedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<AdminResumeVersionItem> versions = List.of();
    }

    @Data
    public static class AdminResumeVersionItem {

        private Long id;
        private Long resumeId;
        private Integer version;
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String parsedContent;
        private String operationType;
        private Long operatorId;
        private String remark;
        private LocalDateTime createdAt;
    }
}
