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
            @NotNull AssetCategory category,   // DOMESTIC_STOCK, FOREIGN_STOCK, CRYPTO, FUND, CASH
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            String currency,     // FOREIGN_STOCK만 사용, 나머지는 서버가 KRW로 확정
            BigDecimal fx,       // FOREIGN_STOCK만 필수(D-063), 그 외 null
            @NotNull @PastOrPresent LocalDate tradeDate   // D-061: 미래 날짜 차단(엔티티에도 이중 방어 있음)
    ) {}

    public record HoldingResponse(
            Long assetId,
            String symbol,
            String name,
            String category,
            String currency,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal currentPrice,       // null이면 시세 조회 실패 → 프론트 배너(D-058)
            BigDecimal evaluationAmount,   // currentPrice * quantity, null 가능
            BigDecimal profitAmount,       // evaluationAmount - 매수총액, null 가능
            BigDecimal profitRate          // %, null 가능. D-049: 양수=빨강/음수=파랑은 프론트 처리
    ) {}
}