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
 * + 조회 실패 시 만료된 캐시값이라도 반환(stale-on-error) — 릴리즈 체크리스트
 *   "장애 시 마지막 조회값 표시" 항목의 실제 구현. 완전 실패(캐시도 없음)일 때만 empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final DomesticStockPriceProvider domesticStockPriceProvider;
    private final ForeignStockPriceProvider foreignStockPriceProvider;
    private final CryptoPriceProvider cryptoPriceProvider;

    private record CacheEntry(BigDecimal price, Instant fetchedAt) {}

    private static final Duration TTL = Duration.ofSeconds(60);
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<BigDecimal> getCurrentPrice(AssetCategory category, String symbol) {
        if (symbol == null) return Optional.empty();

        String key = category + ":" + symbol;
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return Optional.of(cached.price());
        }

        try {
            Optional<BigDecimal> fresh = switch (category) {
                case DOMESTIC_STOCK -> Optional.of(domesticStockPriceProvider.getCurrentPrice(symbol));
                case FOREIGN_STOCK -> Optional.of(foreignStockPriceProvider.getCurrentPrice(symbol));
                case CRYPTO -> Optional.of(cryptoPriceProvider.getCurrentPrice(symbol));
                case FUND, CASH -> Optional.empty(); // 시세 API 미연동, 직접입력 평가금액 사용(변경 없음)
            };
            fresh.ifPresent(p -> cache.put(key, new CacheEntry(p, Instant.now())));
            return fresh;
        } catch (Exception e) {
            log.warn("시세 조회 실패 category={} symbol={} — 캐시 폴백 시도", category, symbol, e);
            return cached != null ? Optional.of(cached.price()) : Optional.empty();
        }
    }
}