package com.nowgnodeel.retirement_planner.asset.stock.controller;

import com.nowgnodeel.retirement_planner.asset.stock.service.OpenDartCorpCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// D-148: 초기 적재 / 급한 갱신용 수동 트리거. DomesticStockController의 /api/admin/domestic-stocks/refresh와 동일 패턴.
@RestController
@RequiredArgsConstructor
public class CorpCodeAdminController {

    private final OpenDartCorpCodeService openDartCorpCodeService;

    @PostMapping("/api/admin/corp-codes/refresh")
    public ResponseEntity<String> refresh() {
        int count = openDartCorpCodeService.refresh();
        return ResponseEntity.ok(count + "건 갱신 완료");
    }
}
