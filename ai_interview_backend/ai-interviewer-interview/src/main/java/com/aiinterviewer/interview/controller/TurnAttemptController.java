package com.aiinterviewer.interview.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.CreateForkAttemptRequest;
import com.aiinterviewer.interview.dto.ForkAttemptDTO;
import com.aiinterviewer.interview.dto.RetryTurnAttemptRequest;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptEventDTO;
import com.aiinterviewer.interview.service.TurnAttemptService;
import com.aiinterviewer.interview.service.ForkAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class TurnAttemptController {

    private final TurnAttemptService turnAttemptService;
    private final ForkAttemptService forkAttemptService;

    @PostMapping("/branches/{branchId}/turn-attempts")
    public Result<TurnAttemptDTO> create(
            @PathVariable("branchId") String branchId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @Valid @RequestBody CreateTurnAttemptRequest request) {
        return Result.success(turnAttemptService.create(branchId, userId, username, request));
    }

    @PostMapping("/branches/{focusedBranchId}/fork-attempts")
    public Result<ForkAttemptDTO> createFork(
            @PathVariable("focusedBranchId") String focusedBranchId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @Valid @RequestBody CreateForkAttemptRequest request) {
        return Result.success(forkAttemptService.create(
                focusedBranchId,
                userId,
                username,
                request));
    }

    @GetMapping("/turn-attempts/{turnId}")
    public Result<TurnAttemptDTO> get(
            @PathVariable("turnId") String turnId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(turnAttemptService.get(turnId, userId));
    }

    @GetMapping(value = "/turn-attempts/{turnId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TurnAttemptEventDTO>> events(
            @PathVariable("turnId") String turnId,
            @RequestHeader("X-User-Id") Long userId) {
        return turnAttemptService.events(turnId, userId)
                .map(event -> ServerSentEvent.<TurnAttemptEventDTO>builder()
                        .event(event.type())
                        .id(event.turnId() + ":" + event.sequence())
                        .data(event)
                        .build());
    }

    @PostMapping("/turn-attempts/{turnId}/retry")
    public Result<TurnAttemptDTO> retry(
            @PathVariable("turnId") String turnId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @Valid @RequestBody RetryTurnAttemptRequest request) {
        return Result.success(turnAttemptService.retry(turnId, userId, username, request));
    }

    @PostMapping("/turn-attempts/{turnId}/cancel")
    public Result<TurnAttemptDTO> cancel(
            @PathVariable("turnId") String turnId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(turnAttemptService.cancel(turnId, userId));
    }

    @PostMapping("/turn-attempts/{turnId}/discard")
    public Result<TurnAttemptDTO> discard(
            @PathVariable("turnId") String turnId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(turnAttemptService.discard(turnId, userId));
    }
}
