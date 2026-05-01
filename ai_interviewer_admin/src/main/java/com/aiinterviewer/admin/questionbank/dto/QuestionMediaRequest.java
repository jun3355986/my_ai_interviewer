package com.aiinterviewer.admin.questionbank.dto;

public record QuestionMediaRequest(
        String type,
        String url,
        String caption,
        String alt) {
}
