package com.aiinterviewer.admin.dashboard;

import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.dashboard.dto.DashboardOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewResponse> overview() {
        return Result.success(dashboardService.getOverview());
    }
}
