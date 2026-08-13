package com.nowgnodeel.retirement_planner.asset.stock.controller;

import com.nowgnodeel.retirement_planner.asset.stock.dto.ForeignStockSearchResult;
import com.nowgnodeel.retirement_planner.asset.stock.service.ForeignStockSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ForeignStockController {

    private final ForeignStockSearchService foreignStockSearchService;

    // 자산 추가 화면 종목검색 자동완성 (해외주식)
    @GetMapping("/api/foreign-stocks/search")
    public ResponseEntity<List<ForeignStockSearchResult>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(foreignStockSearchService.search(keyword));
    }
}
