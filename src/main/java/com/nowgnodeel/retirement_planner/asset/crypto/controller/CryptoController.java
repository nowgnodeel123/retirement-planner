package com.nowgnodeel.retirement_planner.asset.crypto.controller;

import com.nowgnodeel.retirement_planner.asset.crypto.dto.CryptoSearchResult;
import com.nowgnodeel.retirement_planner.asset.crypto.service.CryptoSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CryptoController {

    private final CryptoSearchService cryptoSearchService;

    // 자산 추가 화면 종목검색 자동완성 (코인)
    @GetMapping("/api/crypto/search")
    public ResponseEntity<List<CryptoSearchResult>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(cryptoSearchService.search(keyword));
    }
}
