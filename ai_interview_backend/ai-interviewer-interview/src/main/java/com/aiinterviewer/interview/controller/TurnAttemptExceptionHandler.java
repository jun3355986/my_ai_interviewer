package com.aiinterviewer.interview.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.service.TurnAttemptConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        TurnAttemptController.class,
        StartAttemptController.class
})
public class TurnAttemptExceptionHandler {

    @ExceptionHandler(TurnAttemptConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleConflict(TurnAttemptConflictException exception) {
        return Result.fail(HttpStatus.CONFLICT.value(), exception.getReason());
    }
}
