package com.nowgnodeel.retirement_planner.asset.price;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Override
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
}