package com.aiinterviewer.interview.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.CreateStartAttemptRequest;
import com.aiinterviewer.interview.dto.StartAttemptDTO;
import com.aiinterviewer.interview.service.StartAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class StartAttemptController {

    private final StartAttemptService startAttemptService;

    @PostMapping("/start-attempts")
    public Result<StartAttemptDTO> start(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @Valid @RequestBody CreateStartAttemptRequest request) {
        return Result.success(startAttemptService.create(userId, username, request));
    }
}
