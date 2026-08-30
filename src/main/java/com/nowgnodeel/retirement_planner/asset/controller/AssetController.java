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

    /** 끌어서 바꾼 자산 순서 저장(계좌 스코프). */
    @PatchMapping("/order")
    public ResponseEntity<Void> reorder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AssetReorderRequest request
    ) {
        assetService.reorderAssets(userId, request);
        return ResponseEntity.noContent().build();
    }

    /** 현금·외화 자산 등록/갱신. 계좌당 통화별 1건이라 같은 통화 재등록은 잔액 덮어쓰기다. */
    @PostMapping("/cash")
    public ResponseEntity<HoldingResponse> upsertCash(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CashRequest request
    ) {
        return ResponseEntity.ok(assetService.upsertCash(userId, request));
    }

    /** 자산 표시 이름 변경. */
    @PatchMapping("/{assetId}/name")
    public ResponseEntity<HoldingResponse> rename(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody AssetRenameRequest request
    ) {
        return ResponseEntity.ok(assetService.rename(userId, assetId, request));
    }

    /** 자산 삭제. 거래·배당도 함께 사라진다(FK CASCADE). */
    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId
    ) {
        assetService.delete(userId, assetId);
        return ResponseEntity.noContent().build();
    }

    /** 현금 잔액만 수정. */
    @PatchMapping("/{assetId}/cash")
    public ResponseEntity<HoldingResponse> updateCashBalance(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody CashBalanceRequest request
    ) {
        return ResponseEntity.ok(assetService.updateCashBalance(userId, assetId, request));
    }

    @GetMapping
    public ResponseEntity<List<HoldingResponse>> holdings(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long accountId
    ) {
        return ResponseEntity.ok(assetService.findHoldingsByAccount(userId, accountId));
    }

    /** M6 후속: 잘못 입력한 거래 정정. 매수/매도 구분은 바꿀 수 없다. */
    @PatchMapping("/{assetId}/transactions/{transactionId}")
    public ResponseEntity<HoldingResponse> updateTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @PathVariable Long transactionId,
            @Valid @RequestBody TransactionUpdateRequest request
    ) {
        return ResponseEntity.ok(
                assetService.updateTransaction(userId, assetId, transactionId, request));
    }

    /** M6 후속: 잘못 등록한 거래 삭제. */
    @DeleteMapping("/{assetId}/transactions/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @PathVariable Long transactionId
    ) {
        assetService.deleteTransaction(userId, assetId, transactionId);
        return ResponseEntity.noContent().build();
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
