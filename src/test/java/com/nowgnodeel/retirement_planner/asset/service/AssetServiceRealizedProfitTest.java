package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.Transaction;
import com.nowgnodeel.retirement_planner.asset.entity.TransactionType;
import com.nowgnodeel.retirement_planner.asset.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실현손익·평단 단일 출처(D-107/D-109)의 회귀 테스트. 세금 탭 양도소득세 추정이 같은
 * 값을 쓰므로(R-015) 여기가 틀리면 수익 화면과 세액 추정이 함께 틀린다.
 *
 * 고정하는 것은 둘이다 — 이동평균법(취득원가는 그 매도 "시점"의 평단)과, 해외주식의
 * 환차손익(매수·매도 각각 그 거래에 저장된 fx로 환산).
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceRealizedProfitTest {

    @Mock TransactionRepository transactionRepository;
    @InjectMocks AssetService assetService;

    private static final Long ASSET_ID = 7L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 3, 10);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 6, 20);
    private static final LocalDate DAY_4 = LocalDate.of(2026, 8, 26);

    private Asset asset() {
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(ASSET_ID);
        return asset;
    }

    private Transaction tx(Asset asset, TransactionType type, LocalDate date,
                           String quantity, String unitPrice, String fx) {
        return Transaction.builder()
                .asset(asset)
                .type(type)
                .tradeDate(date)
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(unitPrice))
                .fx(fx == null ? null : new BigDecimal(fx))
                .build();
    }

    private void history(Transaction... txs) {
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAscIdAsc(ASSET_ID))
                .willReturn(List.of(txs));
    }

    @Test
    @DisplayName("해외주식: 매수·매도 각각의 환율로 환산해 환차손익이 실현손익에 포함된다")
    void foreignStockIncludesFxGainLoss() {
        Asset asset = asset();
        Transaction buy = tx(asset, TransactionType.BUY, DAY_1, "10", "180", "1300");
        Transaction sell = tx(asset, TransactionType.SELL, DAY_4, "5", "220", "1380");
        history(buy, sell);

        // 매도 5 × $220 × 1380 = 1,518,000원, 취득 5 × $180 × 1300 = 1,170,000원
        // 환차익 72,000원을 빼먹으면 276,000원이 나온다.
        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("348000");
    }

    @Test
    @DisplayName("해외주식: 환율이 내리면 외화로는 이익이어도 환차손만큼 실현손익이 줄어든다")
    void foreignStockFxLossReducesProfit() {
        Asset asset = asset();
        Transaction buy = tx(asset, TransactionType.BUY, DAY_1, "10", "180", "1400");
        Transaction sell = tx(asset, TransactionType.SELL, DAY_4, "5", "200", "1200");
        history(buy, sell);

        // 매도 5 × $200 × 1200 = 1,200,000원, 취득 5 × $180 × 1400 = 1,260,000원 → 손실
        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("-60000");
    }

    @Test
    @DisplayName("해외주식: 환율이 다른 매수 여러 건은 원화 이동평균으로 취득원가를 낸다")
    void foreignStockAveragesCostInKrwAcrossBuys() {
        Asset asset = asset();
        Transaction firstBuy = tx(asset, TransactionType.BUY, DAY_1, "10", "100", "1000");
        Transaction secondBuy = tx(asset, TransactionType.BUY, DAY_2, "10", "100", "1400");
        Transaction sell = tx(asset, TransactionType.SELL, DAY_4, "5", "150", "1200");
        history(firstBuy, secondBuy, sell);

        // 원화 취득원가 = (10×100×1000 + 10×100×1400) / 20 = 120,000원/주
        // 매도 5 × $150 × 1200 = 900,000원 → 900,000 - 600,000 = 300,000원
        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("국내주식: 실현손익은 (매도단가 - 그 시점 평단) × 수량이다")
    void domesticStockRealizedProfit() {
        Asset asset = asset();
        Transaction buy = tx(asset, TransactionType.BUY, DAY_1, "10", "70000", null);
        Transaction sell = tx(asset, TransactionType.SELL, DAY_4, "5", "80000", null);
        history(buy, sell);

        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("매도 뒤에 다시 매수해도 이미 확정된 과거 매도의 실현손익은 바뀌지 않는다")
    void laterBuyDoesNotChangePastSell() {
        Asset asset = asset();
        Transaction buy = tx(asset, TransactionType.BUY, DAY_1, "10", "100", null);
        Transaction sell = tx(asset, TransactionType.SELL, DAY_2, "5", "150", null);
        Transaction laterBuy = tx(asset, TransactionType.BUY, DAY_3, "10", "200", null);
        history(buy, sell, laterBuy);

        // 매도 시점 평단은 100 → 5 × (150 - 100) = 250.
        // 전체 매수 총평균(150)을 쓰던 시절에는 나중 매수가 소급 반영돼 0원이 나왔다.
        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("부분매도를 반복하면 매도 시점마다 그때의 이동평균 평단을 쓴다")
    void repeatedPartialSellsUseMovingAverageAtEachPoint() {
        Asset asset = asset();
        Transaction firstBuy = tx(asset, TransactionType.BUY, DAY_1, "10", "100", null);
        Transaction firstSell = tx(asset, TransactionType.SELL, DAY_2, "5", "150", null);
        Transaction secondBuy = tx(asset, TransactionType.BUY, DAY_3, "15", "200", null);
        Transaction secondSell = tx(asset, TransactionType.SELL, DAY_4, "4", "300", null);
        history(firstBuy, firstSell, secondBuy, secondSell);

        // 1차 매도: 평단 100 → 5 × 150 - 500 = 250
        assertThat(assetService.calculateRealizedProfitKrw(firstSell)).isEqualByComparingTo("250");
        // 1차 매도 후 잔량 5주·원가 500 → 15주를 200에 더 사면 20주·원가 3,500 → 평단 175
        // 2차 매도: 4 × 300 - 4 × 175 = 1,200 - 700 = 500
        assertThat(assetService.calculateRealizedProfitKrw(secondSell)).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("평단은 매도로 바뀌지 않고 매수로만 바뀐다(이동평균법)")
    void averagePriceFollowsMovingAverage() {
        Asset asset = asset();
        Transaction buy = tx(asset, TransactionType.BUY, DAY_1, "10", "100", null);
        Transaction sell = tx(asset, TransactionType.SELL, DAY_2, "5", "150", null);
        Transaction laterBuy = tx(asset, TransactionType.BUY, DAY_3, "15", "200", null);
        history(buy, sell, laterBuy);

        // 매도 후 잔량 5주·원가 500 → 15주를 200에 더 사면 20주·원가 3,500 → 175
        // 전체 매수 총평균은 (10×100 + 15×200) / 25 = 140으로, 이미 판 5주가 남아 어긋난다.
        assertThat(assetService.calculateAveragePrice(asset)).isEqualByComparingTo("175");
    }

    @Test
    @DisplayName("매수 내역이 없으면 취득원가 0으로 떨어지고 예외를 던지지 않는다")
    void noBuyHistoryYieldsZeroCost() {
        Asset asset = asset();
        Transaction sell = tx(asset, TransactionType.SELL, DAY_4, "5", "220", "1380");
        history(sell);

        assertThat(assetService.calculateRealizedProfitKrw(sell)).isEqualByComparingTo("1518000");
    }
}
