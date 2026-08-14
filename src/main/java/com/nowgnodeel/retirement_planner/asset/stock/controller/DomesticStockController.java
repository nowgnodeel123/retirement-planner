package com.nowgnodeel.retirement_planner.asset.stock.controller;

import com.nowgnodeel.retirement_planner.asset.stock.dto.DividendScheduleResult;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import com.nowgnodeel.retirement_planner.asset.stock.service.DividendScheduleService;
import com.nowgnodeel.retirement_planner.asset.stock.service.DomesticStockMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class DomesticStockController {

    private final DomesticStockRepository domesticStockRepository;
    private final DomesticStockMasterService domesticStockMasterService;
    private final DividendScheduleService dividendScheduleService;

    // 자산 추가 화면 종목검색 자동완성 (국내주식)
    @GetMapping("/api/domestic-stocks/search")
    public ResponseEntity<List<DomesticStock>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(domesticStockRepository.findTop20ByNameContainingOrderByNameAsc(keyword));
    }

    // 초기 적재 / 급한 갱신용 수동 트리거
    @PostMapping("/api/admin/domestic-stocks/refresh")
    public ResponseEntity<String> refresh() {
        int count = domesticStockMasterService.refresh();
        return ResponseEntity.ok(count + "건 갱신 완료");
    }

    // D-148/D-153: 개발단계 검증용 — 아직 프론트 미연결, 라이선스 재검토 전까지 실사용자 노출 금지
    @GetMapping("/api/admin/domestic-stocks/{symbolCode}/dividend-schedule")
    public ResponseEntity<DividendScheduleResult> dividendSchedule(@PathVariable String symbolCode) {
        Optional<DividendScheduleResult> result = dividendScheduleService.lookupByStockCode(symbolCode);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}