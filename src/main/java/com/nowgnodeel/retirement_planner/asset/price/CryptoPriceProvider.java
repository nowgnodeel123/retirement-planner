package com.nowgnodeel.retirement_planner.asset.price;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Upbit 시세 조회는 공개 API — 키 불필요(확인 완료).
 * 자산 통화는 KRW 원화마켓 기준(resolveCurrency)이라 마켓코드는 항상 KRW-{symbol}.
 */
@Component
@RequiredArgsConstructor
public class CryptoPriceProvider implements PriceProvider {

    private final RestClient externalApiRestClient;

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        JsonNode body = externalApiRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.upbit.com")
                        .path("/v1/ticker")
                        .queryParam("markets", "KRW-" + symbol)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (body == null || !body.isArray() || body.isEmpty()) {
            throw new IllegalStateException("코인 시세 조회 실패: " + symbol);
        }
        return new BigDecimal(body.get(0).get("trade_price").asText());
    }
}