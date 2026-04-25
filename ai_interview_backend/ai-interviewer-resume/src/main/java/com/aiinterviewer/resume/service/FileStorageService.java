package com.aiinterviewer.resume.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.resume.config.MinioConfig;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 文件存储服务 (MinIO)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传文件
     *
     * @param file       文件
     * @param folder     文件夹路径
     * @param fileSuffix 文件后缀
     * @return 文件存储路径
     */
    public String uploadFile(MultipartFile file, String folder, String fileSuffix) {
        try {
            ensureBucketExists();

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename, fileSuffix);
            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

            // 构建完整路径
            String filePath = buildFilePath(folder, fileName);

            // 上传到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("文件上传成功: {}", filePath);
            return filePath;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(1000, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件 (带自定义文件名)
     */
    public String uploadFile(MultipartFile file, String folder, String fileName, String contentType) {
        try {
            ensureBucketExists();
            String filePath = buildFilePath(folder, fileName);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());

            log.info("文件上传成功: {}", filePath);
            return filePath;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(1000, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     *
     * @param filePath 文件路径
     * @return 输入流
     */
    public InputStream downloadFile(String filePath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败: {}", filePath, e);
            throw new BusinessException(1000, "文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件URL
     *
     * @param filePath 文件路径
     * @return 文件访问URL
     */
    public String getFileUrl(String filePath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .build()).object();
        } catch (Exception e) {
            log.error("获取文件URL失败: {}", filePath, e);
            throw new BusinessException(1000, "获取文件URL失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     */
    public void deleteFile(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .build());

            log.info("文件删除成功: {}", filePath);
        } catch (Exception e) {
            log.error("文件删除失败: {}", filePath, e);
            throw new BusinessException(1000, "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在
     */
    public boolean fileExists(String filePath) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取文件信息
     *
     * @param filePath 文件路径
     * @return 文件信息
     */
    public FileInfo getFileInfo(String filePath) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(filePath)
                    .build());

            return FileInfo.builder()
                    .filePath(filePath)
                    .size(stat.size())
                    .contentType(stat.contentType())
                    .etag(stat.etag())
                    .lastModified(stat.lastModified().toLocalDateTime())
                    .build();
        } catch (Exception e) {
            log.error("获取文件信息失败: {}", filePath, e);
            throw new BusinessException(1000, "获取文件信息失败: " + e.getMessage());
        }
    }
    
    private void ensureBucketExists() throws Exception {
        String bucket = minioConfig.getBucket();
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket {} created successfully", bucket);
            } catch (Exception e) {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    throw e;
                }
            }
        }
    }

    /**
     * 构建文件路径
     */
    private String buildFilePath(String folder, String fileName) {
        if (folder == null || folder.isEmpty()) {
            return fileName;
        }
        // 移除folder首尾的斜杠
        folder = folder.replaceAll("^/+|/+$", "");
        return folder + "/" + fileName;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String originalFilename, String defaultSuffix) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return defaultSuffix != null ? defaultSuffix : ".pdf";
    }

    /**
     * 文件信息
     */
    @lombok.Data
    @lombok.Builder
    public static class FileInfo {
        private String filePath;
        private long size;
        private String contentType;
        private String etag;
        private java.time.LocalDateTime lastModified;
    }
}
