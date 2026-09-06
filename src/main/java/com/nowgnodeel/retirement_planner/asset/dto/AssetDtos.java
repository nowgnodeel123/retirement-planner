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

    /**
     * 잘못 입력한 매매 거래의 정정. 매수/매도 구분(type)은 바꾸지 않는다 —
     * 방향을 뒤집어야 하면 삭제 후 재등록. assetId/transactionId는 경로에서 받는다.
     */
    public record TransactionUpdateRequest(
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            BigDecimal fx,       // FOREIGN_STOCK만 필수 — BuyRequest/SellRequest와 동일 규칙
            @NotNull @PastOrPresent LocalDate tradeDate
    ) {}

    /**
     * 현금·외화 자산 등록/갱신. 거래(BUY/SELL)가 아니라 잔액을 그대로 받는다 —
     * 계좌당 통화별로 자산 1건만 두고, 같은 통화를 다시 등록하면 잔액을 덮어쓴다.
     */
    public record CashRequest(
            @NotNull Long accountId,
            @NotBlank @Pattern(regexp = "KRW|USD", message = "지원하는 통화는 KRW, USD 입니다.") String currency,
            @NotNull @DecimalMin(value = "0") BigDecimal balance
    ) {}

    /** 자산 표시 이름 변경. 종목코드는 바뀌지 않는다(시세 조회 키라서). */
    public record AssetRenameRequest(
            @NotBlank @Size(max = 100) String name
    ) {}

    /** 자산 상세 화면에서 현금 잔액만 수정. assetId는 경로에서 받는다. */
    public record CashBalanceRequest(
            @NotNull @DecimalMin(value = "0") BigDecimal balance
    ) {}

    /** 사용자가 끌어서 정한 자산 순서. 한 계좌 안에서만 의미가 있어 accountId를 함께 받는다. */
    public record AssetReorderRequest(
            @NotNull Long accountId,
            @NotEmpty java.util.List<Long> orderedIds
    ) {}

    public record HoldingResponse(
            Long assetId,
            Long accountId,
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
            /**
             * 원화 기준 평가손익. profitAmount는 표시통화(해외주식이면 USD) 기준이라
             * 환차손익이 빠져 있다 — 취득원가를 매수 시점 fx로 환산해 다시 계산한 값이
             * 이 필드다. 집계(대시보드·계좌 요약·프리필)는 반드시 이쪽을 쓴다.
             *
             * WHY 두 값을 같이 두나: "종목이 얼마 올랐나"(USD)와 "내 돈이 얼마 늘었나"(KRW)는
             * 해외자산에서 서로 다른 질문이고, 자산 상세는 둘 다 보여줘야 한다.
             * 국내자산은 두 값이 같고, 현금은 매입환율을 안 받으므로 둘 다 null이다.
             */
            BigDecimal krwProfitAmount,
            BigDecimal krwProfitRate,
            String exchangeRateBaseDate,
            Integer sortOrder
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
