package com.nowgnodeel.retirement_planner.asset.price;

import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D-058: 시세 API 실패 시에도 화면이 죽으면 안 되므로 예외를 여기서 흡수한다.
 * + 60초 TTL 메모리 캐시: 페이지 조회마다 자산 수만큼 외부 API를 때리던 문제 해결.
 * + 조회 실패 시 마지막으로 성공한 값이라도 반환(stale-on-error) — 릴리즈 체크리스트
 *   "장애 시 마지막 조회값 표시" 항목의 실제 구현. 완전 실패일 때만 empty.
 *
 * 폴백은 두 층이다. 메모리 캐시가 1차, DB 스냅샷이 2차다.
 * WHY 2차가 필요한가: 예전에는 메모리 캐시뿐이라 외부 시세 서비스가 죽어 있는 동안
 * 백엔드를 재기동하면 폴백할 값이 통째로 사라져 화면 전체가 "시세 조회 실패"가 됐다.
 * 장애 대응 장치가 정작 장애 중 배포·재시작에 무력화되는 구조였다(2026-09-06 data.go.kr
 * 게이트웨이 장애 때 실제로 겪었다).
 *
 * 반환하는 Quote는 값과 함께 "언제 받은 값인지"를 들고 다닌다. 폴백값을 최신 시세인 것처럼
 * 보여주면 안 되고, 화면이 기준 시점을 밝힐 수 있어야 하기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final DomesticStockPriceProvider domesticStockPriceProvider;
    private final ForeignStockPriceProvider foreignStockPriceProvider;
    private final CryptoPriceProvider cryptoPriceProvider;
    private final PriceSnapshotStore snapshotStore;

    /** 시세와 그 값을 실제로 받아온 시각. fetchedAt이 오래됐으면 장애 중 폴백값이라는 뜻이다. */
    public record Quote(BigDecimal price, Instant fetchedAt) {}

    private record CacheEntry(BigDecimal price, Instant fetchedAt) {}

    private static final Duration TTL = Duration.ofSeconds(60);
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<Quote> getQuote(AssetCategory category, String symbol) {
        if (symbol == null) return Optional.empty();

        String key = category + ":" + symbol;
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return Optional.of(new Quote(cached.price(), cached.fetchedAt()));
        }

        try {
            Optional<BigDecimal> fresh = switch (category) {
                case DOMESTIC_STOCK -> Optional.of(domesticStockPriceProvider.getCurrentPrice(symbol));
                case FOREIGN_STOCK -> Optional.of(foreignStockPriceProvider.getCurrentPrice(symbol));
                case CRYPTO -> Optional.of(cryptoPriceProvider.getCurrentPrice(symbol));
                // 시세 API 미연동, 직접입력 평가금액 사용(변경 없음). 폴백 대상도 아니다.
                case FUND, CASH -> Optional.empty();
            };
            if (fresh.isEmpty()) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            cache.put(key, new CacheEntry(fresh.get(), now));
            snapshotStore.save(key, fresh.get(), now);
            return Optional.of(new Quote(fresh.get(), now));
        } catch (Exception e) {
            log.warn("시세 조회 실패 category={} symbol={} — 폴백 시도", category, symbol, e);
            if (cached != null) {
                return Optional.of(new Quote(cached.price(), cached.fetchedAt()));
            }
            return snapshotStore.find(key)
                    .map(s -> new Quote(s.getPrice(), s.getFetchedAt()));
        }
    }
}
