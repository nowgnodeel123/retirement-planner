package com.nowgnodeel.retirement_planner.asset.dto;

import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AssetDtos {

    /**
     * D-053: 종목 검색 → 최초 매수 거래로 자산 생성 통합.
     * 종목 검색 자동완성은 M4(시세 API) 스코프라 M3은 symbol/name 수동 입력.
     */
    public record BuyRequest(
            @NotNull Long accountId,
            @NotBlank String symbol,
            @NotBlank String name,
            @NotNull AssetCategory category,
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            String currency,
            BigDecimal fx,
            @NotNull @PastOrPresent LocalDate tradeDate
    ) {}

    /**
     * M6: 매도 거래. 자산은 이미 존재하므로 accountId/symbol/name/category는 불필요 — assetId만 받는다.
     * D-057(보유수량 초과 검증)은 요청 시점 계산이 필요해 서비스 레이어에서 처리, DTO 자체에는 없음.
     */
    public record SellRequest(
            @NotNull Long assetId,
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            BigDecimal fx,       // FOREIGN_STOCK만 필수(D-063), 그 외 null — BuyRequest와 동일 규칙
            @NotNull @PastOrPresent LocalDate tradeDate   // D-061
    ) {}

    public record HoldingResponse(
            Long assetId,
            String symbol,
            String name,
            String category,
            String currency,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal currentPrice,
            BigDecimal evaluationAmount,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            BigDecimal exchangeRate,
            BigDecimal krwEvaluationAmount,
            String exchangeRateBaseDate
    ) {}

    /**
     * M6: 거래내역 조회 응답. amount = quantity * unitPrice(원 통화 기준 — 해외주식은 USD 그대로).
     * 원화환산은 이 화면 스코프 밖(D-087: 이중표시는 평가금액/손익에만 적용, 거래내역은 원 통화 유지).
     */
    public record TransactionResponse(
            Long transactionId,
            String type,          // "BUY" | "SELL"
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal fx          // FOREIGN_STOCK만 값 존재, 그 외 null
    ) {}
}
