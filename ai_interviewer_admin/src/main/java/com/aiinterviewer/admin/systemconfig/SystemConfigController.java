package com.aiinterviewer.admin.systemconfig;

import com.aiinterviewer.admin.common.model.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public Result<List<SystemConfigService.SystemConfigResponse>> listConfigs(
            @RequestParam(required = false) String configGroup) {
        return Result.success(systemConfigService.listConfigs(configGroup));
    }

    @GetMapping("/{configKey}")
    public Result<SystemConfigService.SystemConfigResponse> getConfig(@PathVariable String configKey) {
        return Result.success(systemConfigService.getConfig(configKey));
    }

    @PutMapping("/{configKey}")
    public Result<Void> updateConfig(
            @PathVariable String configKey,
            @RequestBody SystemConfigService.SystemConfigUpdateRequest request) {
        systemConfigService.updateConfig(configKey, request);
        return Result.success();
    }
}
