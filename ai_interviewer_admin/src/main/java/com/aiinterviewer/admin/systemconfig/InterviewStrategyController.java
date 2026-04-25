package com.aiinterviewer.admin.systemconfig;

import com.aiinterviewer.admin.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/interview-strategy")
@RequiredArgsConstructor
public class InterviewStrategyController {

    private final InterviewStrategyService interviewStrategyService;

    @GetMapping("/default")
    public Result<InterviewStrategyService.DefaultInterviewStrategyResponse> getDefaultStrategy() {
        return Result.success(interviewStrategyService.getDefaultStrategy());
    }

    @PutMapping("/default")
    public Result<Void> saveDefaultStrategy(
            @RequestBody InterviewStrategyService.DefaultInterviewStrategyRequest request) {
        interviewStrategyService.saveDefaultStrategy(request);
        return Result.success();
    }
}
