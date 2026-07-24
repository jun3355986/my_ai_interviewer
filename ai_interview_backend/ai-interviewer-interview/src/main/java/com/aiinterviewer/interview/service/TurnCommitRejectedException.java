package com.aiinterviewer.interview.service;

class TurnCommitRejectedException extends RuntimeException {

    TurnCommitRejectedException(String message) {
        super(message);
    }
}
