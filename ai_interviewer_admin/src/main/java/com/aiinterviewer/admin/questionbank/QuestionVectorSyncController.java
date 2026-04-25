package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/questions/vector-sync")
@RequiredArgsConstructor
public class QuestionVectorSyncController {

    private final QuestionVectorSyncService questionVectorSyncService;

    @PostMapping
    @AdminAudit(module = "QUESTION_BANK", operation = "VECTOR_SYNC", targetType = "QUESTION_VECTOR_SYNC")
    public Result<QuestionVectorSyncService.SyncResult> syncQuestions() {
        return Result.success(questionVectorSyncService.syncPendingQuestions());
    }
}
