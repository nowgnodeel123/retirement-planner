// asset/controller/AssetController.java
package com.nowgnodeel.retirement_planner.asset.controller;

import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.*;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping("/buy")
    public ResponseEntity<HoldingResponse> buy(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BuyRequest request
    ) {
        return ResponseEntity.ok(assetService.buy(userId, request));
    }

    // M6
    @PostMapping("/sell")
    public ResponseEntity<HoldingResponse> sell(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SellRequest request
    ) {
        return ResponseEntity.ok(assetService.sell(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<HoldingResponse>> holdings(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long accountId
    ) {
        return ResponseEntity.ok(assetService.findHoldingsByAccount(userId, accountId));
    }

    // M6: 계좌/자산별 거래내역(매수/매도) 조회
    @GetMapping("/{assetId}/transactions")
    public ResponseEntity<List<TransactionResponse>> transactions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId
    ) {
        return ResponseEntity.ok(assetService.findTransactionsByAsset(userId, assetId));
    }
}
