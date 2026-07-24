// asset/dashboard/controller/DashboardController.java
package com.nowgnodeel.retirement_planner.asset.dashboard.controller;

import com.nowgnodeel.retirement_planner.asset.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.nowgnodeel.retirement_planner.asset.dashboard.dto.DashboardDtos.*;

// M9: 포트폴리오 대시보드 — 총자산/도넛차트 집계 + 이번 달 인사이트 배너
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryResponse> summary(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(dashboardService.getSummary(userId));
    }

    @GetMapping("/insights/monthly")
    public ResponseEntity<MonthlyInsightResponse> monthlyInsight(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(dashboardService.getMonthlyInsight(userId));
    }
}
