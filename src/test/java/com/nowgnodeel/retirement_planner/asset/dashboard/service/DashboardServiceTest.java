package com.nowgnodeel.retirement_planner.asset.dashboard.service;

import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dashboard.dto.DashboardDtos.PortfolioSummaryResponse;
import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.HoldingResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 이 테스트가 고정하는 것은 전부 "화면은 정상인데 숫자가 틀렸던" 결함들이다.
 * 브라우저 QA로는 안 잡히고 시딩 데이터를 손계산과 대조해야만 나왔다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ACC = 10L;

    @Mock private AssetService assetService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private DividendRepository dividendRepository;

    @InjectMocks private DashboardService dashboardService;

    @Test
    @DisplayName("해외주식 평가손익은 환차손익을 포함한다 — 취득원가를 오늘 환율로 환산하지 않는다")
    void foreignHoldingProfitIncludesFxGain() {
        // AAPL 10주를 $100(매수 fx 1,200)에 사서 지금 $319.97, 오늘 fx 1,488.80.
        // 실제 취득원가 = 10 × 100 × 1,200 = 1,200,000원
        // 평가금액     = 10 × 319.97 × 1,488.80 = 4,763,713.36원
        // 손익         = 3,563,713.36원 (수익률 296.98%)
        // 결함 시절에는 취득원가를 오늘 환율로 환산해 1,488,800원으로 보고
        // 손익 3,274,913.36원 / 219.97%를 냈다 — 환차익 288,800원이 통째로 빠졌다.
        HoldingResponse aapl = new HoldingResponse(
                1L, ACC, "AAPL", "애플", AssetCategory.FOREIGN_STOCK.name(), "USD",
                BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("319.97"),
                new BigDecimal("3199.70"), new BigDecimal("2199.70"), new BigDecimal("219.97"),
                new BigDecimal("1488.80"), new BigDecimal("4763713.36"),
                new BigDecimal("3563713.36"), new BigDecimal("296.9761"),
                "2026-07-16", 0);
        given(assetService.findAllHoldingsByUser(USER_ID)).willReturn(List.of(aapl));

        PortfolioSummaryResponse result = dashboardService.getSummary(USER_ID);

        assertThat(result.totalKrw()).isEqualByComparingTo("4763713.36");
        assertThat(result.profitKrw()).isEqualByComparingTo("3563713.36");
        // 취득원가가 매수 시점 fx로 잡혔는지 = 총액 - 손익이 1,200,000원인지로 확인한다.
        assertThat(result.totalKrw().subtract(result.profitKrw())).isEqualByComparingTo("1200000");
    }

    @Test
    @DisplayName("외화 현금은 카테고리가 CASH여도 통화 기준으로 원화환산해 합산한다")
    void foreignCashIsConvertedByCurrencyNotCategory() {
        // $1,000 현금. 카테고리로 분기하면 CASH가 원화환산 분기를 못 타 1,000원으로 더해진다.
        HoldingResponse usdCash = new HoldingResponse(
                2L, ACC, "USD", "미국 달러", AssetCategory.CASH.name(), "USD",
                new BigDecimal("1000"), null, null,
                new BigDecimal("1000"), null, null,
                new BigDecimal("1488.80"), new BigDecimal("1488800"),
                null, null, "2026-07-16", 0);
        given(assetService.findAllHoldingsByUser(USER_ID)).willReturn(List.of(usdCash));

        PortfolioSummaryResponse result = dashboardService.getSummary(USER_ID);

        assertThat(result.totalKrw()).isEqualByComparingTo("1488800");
    }

    @Test
    @DisplayName("전량매도(보유수량 0) 자산은 '시세 미조회로 제외됨' 카운트에 넣지 않는다")
    void fullySoldHoldingIsNotCountedAsExcluded() {
        // M7의 "정리한 자산" 섹션이 있어 정상 사용에서 늘 존재하는 상태다.
        // 제외로 세면 화면이 "N개가 합계에서 빠졌어요"라는 거짓 경보를 띄운다.
        HoldingResponse soldOut = new HoldingResponse(
                3L, ACC, "MSFT", "마이크로소프트", AssetCategory.FOREIGN_STOCK.name(), "USD",
                BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, null, null, null, null, null, null, 0);
        HoldingResponse krwStock = new HoldingResponse(
                4L, ACC, "005930", "삼성전자", AssetCategory.DOMESTIC_STOCK.name(), "KRW",
                BigDecimal.TEN, new BigDecimal("70000"), new BigDecimal("80000"),
                new BigDecimal("800000"), new BigDecimal("100000"), new BigDecimal("14.29"),
                null, null, new BigDecimal("100000"), new BigDecimal("14.29"), null, 0);
        given(assetService.findAllHoldingsByUser(USER_ID)).willReturn(List.of(soldOut, krwStock));

        PortfolioSummaryResponse result = dashboardService.getSummary(USER_ID);

        assertThat(result.excludedCount()).isZero();
        assertThat(result.totalKrw()).isEqualByComparingTo("800000");
    }

    @Test
    @DisplayName("환율을 못 불러온 해외자산은 여전히 제외로 센다(경고가 살아 있어야 한다)")
    void unpricedForeignHoldingStillCountsAsExcluded() {
        HoldingResponse noFx = new HoldingResponse(
                5L, ACC, "TSLA", "테슬라", AssetCategory.FOREIGN_STOCK.name(), "USD",
                BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("200"),
                new BigDecimal("2000"), new BigDecimal("1000"), new BigDecimal("100"),
                null, null, null, null, null, 0);
        given(assetService.findAllHoldingsByUser(USER_ID)).willReturn(List.of(noFx));

        PortfolioSummaryResponse result = dashboardService.getSummary(USER_ID);

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.totalKrw()).isNull();
    }
}
