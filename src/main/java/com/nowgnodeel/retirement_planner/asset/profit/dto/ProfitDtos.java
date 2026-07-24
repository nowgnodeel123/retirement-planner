// asset/profit/dto/ProfitDtos.java
package com.nowgnodeel.retirement_planner.asset.profit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProfitDtos {

    public enum ProfitPeriod { DAY, WEEK, MONTH, YEAR, ALL }

    /**
     * M10(D-065): 계좌 스코프 실현손익(매도)+배당 조회. 요약 합계와 통합 리스트를
     * 한 번의 호출로 함께 내려 수익 탭 화면을 그린다.
     */
    public record ProfitSummaryResponse(
            BigDecimal realizedProfitKrw, // 기간 내 SELL 실현손익 합(원화환산)
            BigDecimal dividendKrw,       // 기간 내 배당 합(원화환산)
            BigDecimal totalProfitKrw,    // realizedProfitKrw + dividendKrw
            int sellCount,
            int dividendCount,
            List<ProfitItem> items        // 최신순, 실현손익/배당 통합
    ) {}

    public record ProfitItem(
            String kind,          // "REALIZED_SELL" | "DIVIDEND"
            Long sourceId,        // transactionId 또는 dividendId
            Long assetId,
            String assetName,
            String category,      // DOMESTIC_STOCK | FOREIGN_STOCK | CRYPTO
            LocalDate date,       // tradeDate 또는 payDate
            BigDecimal amountKrw  // 실현손익(+/-) 또는 배당금액(항상 +), 원화환산
    ) {}
}
