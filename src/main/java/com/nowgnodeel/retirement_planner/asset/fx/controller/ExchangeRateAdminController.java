// src/main/java/com/nowgnodeel/retirement_planner/asset/fx/controller/ExchangeRateAdminController.java

package com.nowgnodeel.retirement_planner.asset.fx.controller;

import com.nowgnodeel.retirement_planner.asset.fx.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExchangeRateAdminController {

    private final ExchangeRateService exchangeRateService;

    @PostMapping("/api/admin/exchange-rates/refresh")
    public ResponseEntity<String> refresh() {
        int count = exchangeRateService.refresh();
        return ResponseEntity.ok(count > 0 ? "환율 갱신 완료" : "환율 갱신 실패(데이터 없음)");
    }
}