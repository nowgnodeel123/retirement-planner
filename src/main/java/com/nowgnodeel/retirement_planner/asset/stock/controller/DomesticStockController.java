package com.nowgnodeel.retirement_planner.asset.stock.controller;

import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import com.nowgnodeel.retirement_planner.asset.stock.service.DomesticStockMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DomesticStockController {

    private final DomesticStockRepository domesticStockRepository;
    private final DomesticStockMasterService domesticStockMasterService;

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
}