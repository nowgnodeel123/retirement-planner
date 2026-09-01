package com.nowgnodeel.retirement_planner.asset.tax.controller;

import com.nowgnodeel.retirement_planner.asset.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;

/**
 * M11(D-064/D-068) → M15(D-232): 인별 세금 요약 — 귀속연도 단위 양도소득세 추정 + 배당소득세 판정.
 *
 * 계좌 스코프(`/api/accounts/{id}/tax`)는 폐지했다. 기본공제 250만원과 금융소득 2천만원 기준이
 * 인별 한도라 계좌별로 계산하면 세법을 잘못 적용하게 되는데, 엔드포인트를 남겨두면 그 잘못된
 * 값이 다시 호출될 수 있다.
 */
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    public ResponseEntity<TaxSummaryResponse> getTax(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer year
    ) {
        int resolvedYear = year != null ? year : Year.now().getValue();
        return ResponseEntity.ok(taxService.getTaxForUser(userId, resolvedYear));
    }
}
