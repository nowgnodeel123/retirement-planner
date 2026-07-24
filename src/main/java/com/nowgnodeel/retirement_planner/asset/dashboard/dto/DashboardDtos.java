// asset/dashboard/dto/DashboardDtos.java
package com.nowgnodeel.retirement_planner.asset.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDtos {

    /**
     * M9: 포트폴리오 대시보드 총자산 요약. 계좌 전체 범위로 확장한
     * 계좌 상세 화면의 "총 평가금액" 계산과 동일한 정책(시세 미조회 자산 제외)을 따른다.
     */
    public record PortfolioSummaryResponse(
            BigDecimal totalKrw,     // 보유자산이 전부 제외되면 null
            BigDecimal profitKrw,
            BigDecimal profitRate,
            int excludedCount,       // 시세 미조회로 제외된 자산 수
            List<CategorySummary> categories
    ) {}

    public record CategorySummary(
            String category,
            BigDecimal totalKrw,
            int assetCount
    ) {}

    /**
     * D-069: "이번 달 매매+배당 요약" 인사이트 배너. Transaction.fx/Dividend.fx는
     * 거래·지급 시점에 이미 저장돼 있으므로(D-063) 실시간 환율 조회 없이 그대로 곱해 환산한다.
     */
    public record MonthlyInsightResponse(
            int buyCount,
            BigDecimal buyAmountKrw,
            int sellCount,
            BigDecimal sellAmountKrw,
            int dividendCount,
            BigDecimal dividendAmountKrw
    ) {}
}
