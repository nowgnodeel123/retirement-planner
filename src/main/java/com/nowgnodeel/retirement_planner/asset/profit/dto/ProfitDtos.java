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
            List<ProfitItem> items,       // 최신순, 실현손익/배당 통합
            // D-237: 기간 경계를 화면에 그대로 보여주기 위해 서버가 계산한 범위를 함께 내린다.
            // 프론트에서 "이번 주/이번 달"을 다시 계산하면 기간 의미가 두 곳에 생겨 어긋난다.
            // ALL이면 둘 다 null.
            LocalDate rangeStart,
            LocalDate rangeEnd,
            // 기간과 무관한 전체 내역 건수. 선택한 기간 밖에 내역이 더 있는지 판단하는 데 쓴다 —
            // 이게 없으면 "이번 달"이 하루뿐인 매월 1일 같은 날 종목이 조용히 사라져
            // 사용자에게는 계산 오류로 보인다(실제 제보 발생).
            int allTimeItemCount
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
