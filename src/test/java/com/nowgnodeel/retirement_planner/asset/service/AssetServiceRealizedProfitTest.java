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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실현손익 단일 출처(D-107/D-109)의 회귀 테스트. 세금 탭 양도소득세 추정이 같은 값을
 * 쓰므로(R-015) 여기가 틀리면 수익 화면과 세액 추정이 함께 틀린다.
 *
 * 핵심은 해외주식의 환차손익이다 — 매수 fx가 저장돼 있는데도 매도일 fx 하나만 곱하던
 * 시절에는 환차손익이 통째로 빠졌다.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceRealizedProfitTest {

    @Mock TransactionRepository transactionRepository;
    @InjectMocks AssetService assetService;

    private static final Long ASSET_ID = 7L;

    private Asset asset() {
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(ASSET_ID);
        return asset;
    }

    private Transaction tx(TransactionType type, String quantity, String unitPrice, String fx) {
        Transaction t = mock(Transaction.class);
        when(t.getType()).thenReturn(type);
        when(t.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(t.getUnitPrice()).thenReturn(new BigDecimal(unitPrice));
        if (fx != null) {
            when(t.getFx()).thenReturn(new BigDecimal(fx));
        }
        return t;
    }

    /** 매도 거래는 자산과 fx만 조회되므로 lenient한 최소 스텁으로 만든다. */
    private Transaction sell(Asset asset, String quantity, String unitPrice, String fx) {
        Transaction t = mock(Transaction.class);
        when(t.getAsset()).thenReturn(asset);
        when(t.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(t.getUnitPrice()).thenReturn(new BigDecimal(unitPrice));
        when(t.getFx()).thenReturn(fx == null ? null : new BigDecimal(fx));
        return t;
    }

    @Test
    @DisplayName("해외주식: 매수·매도 각각의 환율로 환산해 환차손익이 실현손익에 포함된다")
    void foreignStockIncludesFxGainLoss() {
        Asset asset = asset();
        Transaction buy = tx(TransactionType.BUY, "10", "180", "1300");
        Transaction sellTx = sell(asset, "5", "220", "1380");
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAsc(ASSET_ID))
                .willReturn(List.of(buy));

        BigDecimal profit = assetService.calculateRealizedProfitKrw(sellTx);

        // 매도 5 × $220 × 1380 = 1,518,000원, 취득 5 × $180 × 1300 = 1,170,000원
        // 환차익 72,000원을 빼먹으면 276,000원이 나온다.
        assertThat(profit).isEqualByComparingTo("348000");
    }

    @Test
    @DisplayName("해외주식: 환율이 내리면 외화로는 이익이어도 환차손만큼 실현손익이 줄어든다")
    void foreignStockFxLossReducesProfit() {
        Asset asset = asset();
        Transaction buy = tx(TransactionType.BUY, "10", "180", "1400");
        Transaction sellTx = sell(asset, "5", "200", "1200");
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAsc(ASSET_ID))
                .willReturn(List.of(buy));

        BigDecimal profit = assetService.calculateRealizedProfitKrw(sellTx);

        // 매도 5 × $200 × 1200 = 1,200,000원, 취득 5 × $180 × 1400 = 1,260,000원 → 손실
        assertThat(profit).isEqualByComparingTo("-60000");
    }

    @Test
    @DisplayName("해외주식: 환율이 다른 매수 여러 건은 원화 총평균으로 취득원가를 낸다")
    void foreignStockAveragesCostInKrwAcrossBuys() {
        Asset asset = asset();
        Transaction firstBuy = tx(TransactionType.BUY, "10", "100", "1000");
        Transaction secondBuy = tx(TransactionType.BUY, "10", "100", "1400");
        Transaction sellTx = sell(asset, "5", "150", "1200");
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAsc(ASSET_ID))
                .willReturn(List.of(firstBuy, secondBuy));

        BigDecimal profit = assetService.calculateRealizedProfitKrw(sellTx);

        // 원화 취득원가 총평균 = (10×100×1000 + 10×100×1400) / 20 = 120,000원/주
        // 매도 5 × $150 × 1200 = 900,000원 → 900,000 - 600,000 = 300,000원
        assertThat(profit).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("국내주식(fx 없음): 계산 경로가 이전과 동일하다")
    void domesticStockUnchanged() {
        Asset asset = asset();
        Transaction buy = tx(TransactionType.BUY, "10", "70000", null);
        Transaction sellTx = sell(asset, "5", "80000", null);
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAsc(ASSET_ID))
                .willReturn(List.of(buy));

        BigDecimal profit = assetService.calculateRealizedProfitKrw(sellTx);

        assertThat(profit).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("매수 내역이 없으면 취득원가 0으로 떨어지고 예외를 던지지 않는다")
    void noBuyHistoryYieldsZeroCost() {
        Asset asset = asset();
        Transaction sellTx = sell(asset, "5", "220", "1380");
        given(transactionRepository.findAllByAssetIdOrderByTradeDateAsc(ASSET_ID))
                .willReturn(List.of());

        BigDecimal profit = assetService.calculateRealizedProfitKrw(sellTx);

        assertThat(profit).isEqualByComparingTo("1518000");
    }
}
