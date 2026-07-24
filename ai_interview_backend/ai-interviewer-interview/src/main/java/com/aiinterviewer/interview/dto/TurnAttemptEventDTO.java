package com.aiinterviewer.interview.dto;

import java.time.LocalDateTime;

public record TurnAttemptEventDTO(
        String turnId,
        long sequence,
        String type,
        String status,
        LocalDateTime occurredAt) {
}
