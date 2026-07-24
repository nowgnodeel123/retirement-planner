// asset/profit/controller/ProfitController.java
package com.nowgnodeel.retirement_planner.asset.profit.controller;

import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.profit.service.ProfitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nowgnodeel.retirement_planner.asset.profit.dto.ProfitDtos.*;

// M10(D-065): 계좌 스코프 실현손익+배당 조회
@RestController
@RequestMapping("/api/accounts/{accountId}/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitService profitService;

    @GetMapping
    public ResponseEntity<ProfitSummaryResponse> getProfit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "MONTH") ProfitPeriod period,
            @RequestParam(required = false) AssetCategory category
    ) {
        return ResponseEntity.ok(profitService.getProfit(userId, accountId, period, category));
    }
}
