package com.nowgnodeel.retirement_planner.asset.price;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ForeignStockPriceProvider implements PriceProvider {

    private final RestClient externalApiRestClient;

    @Value("${price-api.finnhub.key}")
    private String apiKey;

    // M14(D-162 후속): Finnhub 연속 장애 시 회로를 열어 타임아웃 대기 없이 바로 실패시킨다.
    // PriceService의 캐시 폴백은 그대로 동작 — 여기서는 예외를 감추지 않고 다시 던진다.
    @Override
    @CircuitBreaker(name = "foreignStockPrice", fallbackMethod = "fallback")
    public BigDecimal getCurrentPrice(String symbol) {
        JsonNode body = externalApiRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("finnhub.io")
                        .path("/api/v1/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        // Finnhub는 종목이 없어도 200 + c:0을 반환하므로 0 방어 필요
        if (body == null || !body.hasNonNull("c") || body.get("c").asDouble() == 0) {
            throw new IllegalStateException("해외주식 시세 조회 실패: " + symbol);
        }
        return BigDecimal.valueOf(body.get("c").asDouble());
    }

    private BigDecimal fallback(String symbol, Throwable t) {
        throw new IllegalStateException("해외주식 시세 조회 실패(서킷 오픈 또는 하위 예외): " + symbol, t);
    }
}