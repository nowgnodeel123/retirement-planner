// asset/tax/controller/TaxController.java
package com.nowgnodeel.retirement_planner.asset.tax.controller;

import com.nowgnodeel.retirement_planner.asset.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

import static com.nowgnodeel.retirement_planner.asset.tax.dto.TaxDtos.*;

// M11(D-064/D-068): 계좌 스코프 세금 탭 — 귀속연도 단위 양도소득세 추정+배당소득세 판정
@RestController
@RequestMapping("/api/accounts/{accountId}/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    public ResponseEntity<TaxSummaryResponse> getTax(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long accountId,
            @RequestParam(required = false) Integer year
    ) {
        int resolvedYear = year != null ? year : Year.now().getValue();
        return ResponseEntity.ok(taxService.getTax(userId, accountId, resolvedYear));
    }
}
