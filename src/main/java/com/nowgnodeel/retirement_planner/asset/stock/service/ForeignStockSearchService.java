package com.nowgnodeel.retirement_planner.asset.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.dto.ForeignStockSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Finnhub는 심볼 검색 엔드포인트를 제공하므로(무료 티어 포함),
 * 국내주식(D-086)과 달리 로컬 캐시 없이 매 요청을 그대로 프록시한다.
 */
@Service
@RequiredArgsConstructor
public class ForeignStockSearchService {

    private static final int MAX_RESULTS = 20;

    private final RestClient externalApiRestClient;

    @Value("${price-api.finnhub.key}")
    private String apiKey;

    public List<ForeignStockSearchResult> search(String keyword) {
        JsonNode body = externalApiRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("finnhub.io")
                        .path("/api/v1/search")
                        .queryParam("q", keyword)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (body == null || !body.has("result")) {
            return List.of();
        }

        List<ForeignStockSearchResult> results = new ArrayList<>();
        for (JsonNode node : body.get("result")) {
            if (results.size() >= MAX_RESULTS) break;
            results.add(new ForeignStockSearchResult(
                    node.path("symbol").asText(),
                    node.path("description").asText(),
                    node.path("type").asText()
            ));
        }
        return results;
    }
}
