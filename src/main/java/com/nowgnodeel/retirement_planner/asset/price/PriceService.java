package com.nowgnodeel.retirement_planner.asset.price;

import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * D-058: 시세 API 실패 시에도 화면이 죽으면 안 되므로 예외를 여기서 흡수하고
 * Optional.empty()로 반환한다 — 프론트는 null이면 "시세 조회 실패" 배너를 띄운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final DomesticStockPriceProvider domesticStockPriceProvider;
    private final ForeignStockPriceProvider foreignStockPriceProvider;
    private final CryptoPriceProvider cryptoPriceProvider;

    public Optional<BigDecimal> getCurrentPrice(AssetCategory category, String symbol) {
        if (symbol == null) return Optional.empty();

        try {
            return switch (category) {
                case DOMESTIC_STOCK -> Optional.of(domesticStockPriceProvider.getCurrentPrice(symbol));
                case FOREIGN_STOCK -> Optional.of(foreignStockPriceProvider.getCurrentPrice(symbol));
                case CRYPTO -> Optional.of(cryptoPriceProvider.getCurrentPrice(symbol));
                case FUND, CASH -> Optional.empty(); // 시세 API 미연동, 직접입력 평가금액 사용(변경 없음)
            };
        } catch (Exception e) {
            log.warn("시세 조회 실패 category={} symbol={}", category, symbol, e);
            return Optional.empty();
        }
    }
}