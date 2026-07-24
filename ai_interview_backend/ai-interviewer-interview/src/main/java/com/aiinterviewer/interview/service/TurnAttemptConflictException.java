package com.aiinterviewer.interview.service;

import lombok.Getter;

@Getter
public class TurnAttemptConflictException extends RuntimeException {

    private final String reason;

    public TurnAttemptConflictException(String reason) {
        super(reason);
        this.reason = reason;
    }
}
