package com.nowgnodeel.retirement_planner.asset.price;

import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * 2026-09-06 data.go.kr 게이트웨이 장애에서 드러난 것을 고정한다.
 * 장애 자체는 외부 문제였지만, 그때 백엔드를 재기동하자 폴백값이 통째로 사라져
 * 화면 전체가 "시세 조회 실패"가 된 것은 우리 구조의 문제였다.
 */
@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    private static final String SYMBOL = "005930";
    private static final String KEY = "DOMESTIC_STOCK:005930";

    @Mock private DomesticStockPriceProvider domesticStockPriceProvider;
    @Mock private ForeignStockPriceProvider foreignStockPriceProvider;
    @Mock private CryptoPriceProvider cryptoPriceProvider;
    @Mock private PriceSnapshotStore snapshotStore;

    @InjectMocks private PriceService priceService;

    @Test
    @DisplayName("조회에 성공하면 값과 조회 시각을 함께 돌려주고 스냅샷에 남긴다")
    void successStoresSnapshot() {
        given(domesticStockPriceProvider.getCurrentPrice(SYMBOL)).willReturn(new BigDecimal("70000"));

        Optional<PriceService.Quote> quote = priceService.getQuote(AssetCategory.DOMESTIC_STOCK, SYMBOL);

        assertThat(quote).isPresent();
        assertThat(quote.get().price()).isEqualByComparingTo("70000");
        assertThat(quote.get().fetchedAt()).isNotNull();
        then(snapshotStore).should().save(eq(KEY), eq(new BigDecimal("70000")), any(Instant.class));
    }

    @Test
    @DisplayName("재기동 직후 외부 API가 죽어 있어도 DB 스냅샷으로 마지막 시세를 돌려준다")
    void fallsBackToSnapshotWhenMemoryCacheIsEmpty() {
        // 메모리 캐시가 빈 상태 = 방금 재기동한 상태. 예전에는 여기서 곧장 empty가 나갔다.
        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);
        willThrow(new IllegalStateException("Read timed out"))
                .given(domesticStockPriceProvider).getCurrentPrice(SYMBOL);
        given(snapshotStore.find(KEY))
                .willReturn(Optional.of(new PriceSnapshot(KEY, new BigDecimal("68500"), threeDaysAgo)));

        Optional<PriceService.Quote> quote = priceService.getQuote(AssetCategory.DOMESTIC_STOCK, SYMBOL);

        assertThat(quote).isPresent();
        assertThat(quote.get().price()).isEqualByComparingTo("68500");
        // 3일 전 값이라는 사실이 화면까지 전달돼야 한다 — 최신 시세인 척하면 안 된다.
        assertThat(quote.get().fetchedAt()).isEqualTo(threeDaysAgo);
    }

    @Test
    @DisplayName("스냅샷도 없으면 empty — 없는 값을 지어내지 않는다")
    void returnsEmptyWhenNoSnapshotEither() {
        willThrow(new IllegalStateException("Read timed out"))
                .given(domesticStockPriceProvider).getCurrentPrice(SYMBOL);
        given(snapshotStore.find(KEY)).willReturn(Optional.empty());

        assertThat(priceService.getQuote(AssetCategory.DOMESTIC_STOCK, SYMBOL)).isEmpty();
    }

    @Test
    @DisplayName("실패는 스냅샷에 쓰지 않는다 — '마지막으로 성공한 값'이라는 의미가 깨진다")
    void failureDoesNotOverwriteSnapshot() {
        willThrow(new IllegalStateException("Read timed out"))
                .given(domesticStockPriceProvider).getCurrentPrice(SYMBOL);
        given(snapshotStore.find(KEY)).willReturn(Optional.empty());

        priceService.getQuote(AssetCategory.DOMESTIC_STOCK, SYMBOL);

        then(snapshotStore).should(never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("현금·펀드는 시세 API를 안 쓰므로 스냅샷을 뒤지지도 않는다")
    void cashDoesNotTouchSnapshotStore() {
        assertThat(priceService.getQuote(AssetCategory.CASH, "USD")).isEmpty();

        then(snapshotStore).should(never()).find(any());
        then(snapshotStore).should(never()).save(any(), any(), any());
    }
}
