package com.ecommerce.adminservice.controller;

import com.ecommerce.adminservice.dto.DashboardOverview;
import com.ecommerce.adminservice.service.DashboardService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台看板接口。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 看板概览 */
    @GetMapping("/overview")
    public Result<DashboardOverview> getOverview() {
        return Result.success(dashboardService.getOverview());
    }
}
