package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.questionbank.entity.QuestionImportBatch;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/questions/import")
@RequiredArgsConstructor
public class QuestionImportController {

    private final QuestionImportService questionImportService;

    @PostMapping
    @AdminAudit(module = "QUESTION_BANK", operation = "IMPORT", targetType = "QUESTION_IMPORT_BATCH")
    public Result<QuestionImportBatch> importQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long importedBy) {
        try {
            return Result.success(questionImportService.importCsv(file.getOriginalFilename(), file.getInputStream(), importedBy));
        } catch (IOException ex) {
            throw new AdminBusinessException(400, "导入文件读取失败");
        }
    }
}
