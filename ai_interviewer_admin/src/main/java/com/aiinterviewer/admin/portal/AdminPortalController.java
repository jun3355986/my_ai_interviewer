package com.aiinterviewer.admin.portal;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.Result;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 门户聚合入口：简历上传入库 + Python 模型/检索运行时配置代理。
 */
@RestController
@RequestMapping("/admin/portal")
@RequiredArgsConstructor
public class AdminPortalController {

    private final AdminResumePortalService resumePortalService;
    private final PythonPortalClient pythonPortalClient;
    private final PortalIdentityResolver identityResolver;

    @PostMapping("/resumes")
    @AdminAudit(module = "INTERVIEW_PORTAL", operation = "UPLOAD_RESUME", targetType = "RESUME")
    public Result<AdminResumePortalService.ResumeUploadResponse> uploadResume(
            @AuthenticationPrincipal Long adminUserId,
            @RequestPart("file") MultipartFile file) {
        PortalIdentityResolver.PortalIdentity identity = identityResolver.requireIdentity(adminUserId);
        try {
            return Result.success(resumePortalService.parseAndSave(identity.id(), file));
        } catch (IllegalArgumentException ex) {
            throw new AdminBusinessException(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new AdminBusinessException(502, ex.getMessage());
        }
    }

    @GetMapping("/model-config")
    public ResponseEntity<String> getModelConfig() {
        PythonPortalClient.ExchangeResponse upstream = pythonPortalClient.getRuntimeConfig();
        return exchange(upstream);
    }

    @PutMapping("/model-config")
    @AdminAudit(module = "MODEL_CONFIG", operation = "UPDATE_RUNTIME_CONFIG", targetType = "MODEL_CONFIG")
    public ResponseEntity<String> updateModelConfig(@RequestBody String body) {
        PythonPortalClient.ExchangeResponse upstream = pythonPortalClient.putRuntimeConfig(body);
        return exchange(upstream);
    }

    @PostMapping("/model-config/test")
    @AdminAudit(module = "MODEL_CONFIG", operation = "TEST_RUNTIME_CONFIG", targetType = "MODEL_CONFIG")
    public ResponseEntity<String> testModelConfig() {
        PythonPortalClient.ExchangeResponse upstream = pythonPortalClient.testRuntimeConfig();
        return exchange(upstream);
    }

    private ResponseEntity<String> exchange(PythonPortalClient.ExchangeResponse upstream) {
        return ResponseEntity.status(upstream.status())
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(upstream.body());
    }
}
