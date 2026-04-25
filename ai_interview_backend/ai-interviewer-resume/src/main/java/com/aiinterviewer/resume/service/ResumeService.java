package com.aiinterviewer.resume.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.resume.dto.ParseResumeResponse;
import com.aiinterviewer.resume.dto.ResumeDTO;
import com.aiinterviewer.resume.dto.ResumeParseRequest;
import com.aiinterviewer.resume.dto.ResumeUploadRequest;
import com.aiinterviewer.resume.dto.VersionDTO;
import com.aiinterviewer.resume.entity.Resume;
import com.aiinterviewer.resume.entity.ResumeContent;
import com.aiinterviewer.resume.entity.ResumeVersion;
import com.aiinterviewer.resume.mapper.ResumeMapper;
import com.aiinterviewer.resume.mapper.ResumeVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * 简历服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final FileStorageService fileStorageService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${python.ai.base-url:${python-ai.base-url:http://localhost:8000}}")
    private String pythonAiBaseUrl;

    @PostConstruct
    public void init() {
        log.info("ResumeService initialized with python.ai.base-url: {}", pythonAiBaseUrl);
    }

    /**
     * 上传简历
     */
    @Transactional
    public ResumeDTO uploadResume(Long userId, MultipartFile file, ResumeUploadRequest request) {
        log.info("用户 {} 上传简历: {}", userId, file.getOriginalFilename());

        // 生成文件路径
        String folder = "resumes/" + userId + "/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filePath = fileStorageService.uploadFile(file, folder, null);

        // 创建简历记录
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename()));
        resume.setOriginalFileName(file.getOriginalFilename());
        resume.setFilePath(filePath);
        resume.setFileSize(file.getSize());
        resume.setContentType(file.getContentType());
        resume.setParseStatus(0); // 未解析
        resume.setIsDefault(request.getSetAsDefault() != null && request.getSetAsDefault());

        // 如果设为默认,取消其他默认
        if (Boolean.TRUE.equals(resume.getIsDefault())) {
            resumeMapper.clearDefaultByUserId(userId);
        }

        resume.setVersionCount(1);
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());

        resumeMapper.insert(resume);

        // 创建版本记录
        ResumeVersion version = new ResumeVersion();
        version.setResumeId(resume.getId());
        version.setVersion(1);
        version.setFilePath(filePath);
        version.setFileName(file.getOriginalFilename());
        version.setFileSize(file.getSize());
        version.setOperationType("UPLOAD");
        version.setOperatorId(userId);
        version.setRemark(request.getRemark());
        version.setCreatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(version);

        return toDTO(resume);
    }

    /**
     * 解析简历
     */
    @Transactional
    public ParseResumeResponse parseResume(Long resumeId, Long userId, ResumeParseRequest request) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此简历");
        }

        // 检查是否已解析且不复用
        if (resume.getParseStatus() == 2 && !Boolean.TRUE.equals(request.getForceReparse())) {
            ParseResumeResponse response = new ParseResumeResponse();
            response.setResumeId(resumeId);
            response.setParseStatus(resume.getParseStatus());
            response.setParseStatusText("已解析");
            response.setContent(resume.getParsedContent());
            response.setParsedAt(resume.getParsedAt());
            return response;
        }

        // 更新解析状态为解析中
        resume.setParseStatus(1);
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(resume);

        try {
            // 获取文件流
            ResumeContent content;
            try (InputStream fileStream = fileStorageService.downloadFile(resume.getFilePath())) {
                // 调用Python后端解析
                content = callPythonParse(fileStream, resume.getContentType());
            }

            // 更新简历解析结果
            resume.setParsedContent(content);
            resume.setParseStatus(2);
            resume.setParseError(null);
            resume.setParsedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(resume);

            // 创建重新解析版本记录
            ResumeVersion version = new ResumeVersion();
            version.setResumeId(resumeId);
            version.setVersion(resume.getVersionCount() + 1);
            version.setFilePath(resume.getFilePath());
            version.setFileName(resume.getOriginalFileName());
            version.setFileSize(resume.getFileSize());
            version.setParsedContent(content);
            version.setOperationType("REPARSE");
            version.setOperatorId(userId);
            version.setRemark(request.getRemark());
            version.setCreatedAt(LocalDateTime.now());
            resumeVersionMapper.insert(version);

            // 更新版本计数
            resume.setVersionCount(resume.getVersionCount() + 1);
            resumeMapper.updateById(resume);

            ParseResumeResponse response = new ParseResumeResponse();
            response.setResumeId(resumeId);
            response.setParseStatus(2);
            response.setParseStatusText("解析成功");
            response.setContent(content);
            response.setParsedAt(resume.getParsedAt());

            return response;
        } catch (Exception e) {
            log.error("简历解析失败: {}", resumeId, e);

            // 更新解析状态为失败
            resume.setParseStatus(3);
            resume.setParseError(e.getMessage());
            resume.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(resume);

            ParseResumeResponse response = new ParseResumeResponse();
            response.setResumeId(resumeId);
            response.setParseStatus(3);
            response.setParseStatusText("解析失败");
            response.setErrorMessage(e.getMessage());

            return response;
        }
    }

    /**
     * 获取简历列表
     */
    public List<ResumeDTO> listResumes(Long userId) {
        List<Resume> resumes = resumeMapper.selectByUserId(userId);
        return resumes.stream().map(this::toDTO).toList();
    }

    /**
     * 获取简历详情
     */
    public ResumeDTO getResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此简历");
        }
        return toDTO(resume);
    }

    /**
     * 删除简历
     */
    @Transactional
    public void deleteResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此简历");
        }

        // 删除文件
        try {
            fileStorageService.deleteFile(resume.getFilePath());
        } catch (Exception e) {
            log.warn("删除文件失败: {}", resume.getFilePath(), e);
        }

        // 删除版本记录
        List<ResumeVersion> versions = resumeVersionMapper.selectByResumeId(resumeId);
        List<Long> versionIds = versions.stream().map(ResumeVersion::getId).toList();
        if (!versionIds.isEmpty()) {
            resumeVersionMapper.deleteBatchIds(versionIds);
        }

        // 删除简历记录
        resumeMapper.deleteById(resumeId);

        log.info("简历删除成功: {}", resumeId);
    }

    /**
     * 设为默认简历
     */
    @Transactional
    public void setDefault(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此简历");
        }

        // 取消其他默认
        resumeMapper.clearDefaultByUserId(userId);

        // 设置当前为默认
        resume.setIsDefault(true);
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(resume);

        log.info("简历 {} 设为默认", resumeId);
    }

    /**
     * 获取版本历史
     */
    public List<VersionDTO> getVersionHistory(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此简历");
        }

        List<ResumeVersion> versions = resumeVersionMapper.selectByResumeId(resumeId);
        return versions.stream().map(this::toVersionDTO).toList();
    }

    /**
     * 获取默认简历
     */
    public ResumeDTO getDefaultResume(Long userId) {
        Resume resume = resumeMapper.selectDefaultByUserId(userId);
        if (resume == null) {
            return null;
        }
        return toDTO(resume);
    }

    /**
     * 调用Python后端解析简历
     */
    private ResumeContent callPythonParse(InputStream fileStream, String contentType) {
        Path tempFile = null;
        WebClient webClient = webClientBuilder.baseUrl(pythonAiBaseUrl).build();

        try {
            // 创建临时文件
            tempFile = Files.createTempFile("resume_", ".tmp");
            Files.copy(fileStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                String result = invokeParseEndpoint(webClient, tempFile, "/resume/parse");
                return parseResumeContent(result);
            } catch (WebClientResponseException.NotFound notFound) {
                log.warn("Python端点 /resume/parse 返回404，尝试兼容回退到 /interview/upload-resume");
                String fallbackResult = invokeParseEndpoint(webClient, tempFile, "/interview/upload-resume");
                return parseResumeContent(fallbackResult);
            }
        } catch (WebClientResponseException e) {
            throw toAiServiceBusinessException(e, "Python解析接口调用失败");
        } catch (WebClientRequestException e) {
            throw toAiServiceBusinessException(e);
        } catch (IOException e) {
            log.error("处理简历临时文件失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "简历解析服务异常: 临时文件处理失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用Python解析失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "简历解析服务异常: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    log.warn("清理简历临时文件失败: {}", tempFile, cleanupEx);
                }
            }
        }
    }

    private String invokeParseEndpoint(WebClient webClient, Path tempFile, String endpoint) {
        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", new org.springframework.core.io.FileSystemResource(tempFile)))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private ResumeContent parseResumeContent(String result) {
        try {
            return objectMapper.readValue(result, ResumeContent.class);
        } catch (IOException ignored) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(result);
                com.fasterxml.jackson.databind.JsonNode data = root;
                if (root.hasNonNull("content")) {
                    data = root.get("content");
                } else if (root.hasNonNull("parsedContent")) {
                    data = root.get("parsedContent");
                }
                return objectMapper.treeToValue(data, ResumeContent.class);
            } catch (Exception ex) {
                log.error("Python简历解析响应反序列化失败，原始响应: {}", result, ex);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "简历解析服务异常: 响应格式不受支持");
            }
        }
    }

    private BusinessException toAiServiceBusinessException(WebClientResponseException e, String prefix) {
        String body = e.getResponseBodyAsString();
        String message = String.format("%s: status=%s, body=%s", prefix, e.getRawStatusCode(), safeSnippet(body));
        log.error(message, e);
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, message);
    }

    private BusinessException toAiServiceBusinessException(WebClientRequestException e) {
        Throwable cause = e.getCause();
        String base = "Python解析接口连接或超时异常";
        if (cause instanceof TimeoutException) {
            base = "Python解析接口调用超时";
        } else if (cause instanceof ConnectException) {
            base = "Python解析接口连接失败";
        } else if (cause != null && cause.getClass().getSimpleName().toLowerCase().contains("timeout")) {
            base = "Python解析接口调用超时";
        }
        String message = String.format("%s: %s", base, e.getMessage());
        log.error(message, e);
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, message);
    }

    private String safeSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /**
     * 转换为DTO
     */
    private ResumeDTO toDTO(Resume resume) {
        ResumeDTO dto = new ResumeDTO();
        dto.setId(resume.getId());
        dto.setUserId(resume.getUserId());
        dto.setFileName(resume.getFileName());
        dto.setOriginalFileName(resume.getOriginalFileName());
        dto.setFileSize(formatFileSize(resume.getFileSize()));
        dto.setContentType(resume.getContentType());
        dto.setParseStatus(resume.getParseStatus());
        dto.setParseStatusText(getParseStatusText(resume.getParseStatus()));
        dto.setParsedContent(resume.getParsedContent());
        dto.setIsDefault(resume.getIsDefault());
        dto.setVersionCount(resume.getVersionCount());
        dto.setCreatedAt(resume.getCreatedAt());
        dto.setParsedAt(resume.getParsedAt());

        // 从解析内容提取关键信息
        if (resume.getParsedContent() != null) {
            ResumeContent content = resume.getParsedContent();
            dto.setName(content.getName());
            dto.setEducation(content.getEducation());
            dto.setUniversity(content.getUniversity());
            dto.setWorkYears(content.getWorkYears());
            dto.setJobIntent(content.getJobIntent());
        }

        return dto;
    }

    /**
     * 转换为版本DTO
     */
    private VersionDTO toVersionDTO(ResumeVersion version) {
        VersionDTO dto = new VersionDTO();
        dto.setId(version.getId());
        dto.setResumeId(version.getResumeId());
        dto.setVersion(version.getVersion());
        dto.setFileName(version.getFileName());
        dto.setFileSize(formatFileSize(version.getFileSize()));
        dto.setOperationType(version.getOperationType());
        dto.setRemark(version.getRemark());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setContent(version.getParsedContent());
        return dto;
    }

    private String getParseStatusText(Integer status) {
        return switch (status) {
            case 0 -> "未解析";
            case 1 -> "解析中";
            case 2 -> "已解析";
            case 3 -> "解析失败";
            default -> "未知";
        };
    }

    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".pdf";
        return filename.substring(filename.lastIndexOf("."));
    }
}
