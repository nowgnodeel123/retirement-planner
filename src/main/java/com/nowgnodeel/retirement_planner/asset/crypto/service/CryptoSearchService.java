package com.nowgnodeel.retirement_planner.asset.crypto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.crypto.dto.CryptoSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Upbit는 심볼 검색 엔드포인트가 따로 없고 전체 마켓 목록(/v1/market/all)만 제공한다
 * (해외주식 Finnhub와 다른 점 — D-139 참고). 요청마다 전체 목록을 새로 받으면 타이핑
 * 할 때마다 외부 API를 두들기게 되므로, 목록 자체(자주 안 바뀜)를 5분만 메모리에
 * 캐싱하고 검색어 매칭만 매 요청 수행한다. 원화(KRW)마켓만 남긴다 —
 * CryptoPriceProvider도 KRW 마켓 기준으로 시세를 조회한다(자산 통화 정책 통일).
 */
@Service
@RequiredArgsConstructor
public class CryptoSearchService {

    private static final int MAX_RESULTS = 20;
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000;

    private final RestClient externalApiRestClient;

    private record MarketEntry(String symbol, String koreanName, String englishName) {}

    private final AtomicReference<List<MarketEntry>> cache = new AtomicReference<>();
    private volatile long cachedAt = 0;

    public List<CryptoSearchResult> search(String keyword) {
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        List<CryptoSearchResult> results = new ArrayList<>();
        for (MarketEntry entry : allMarkets()) {
            if (results.size() >= MAX_RESULTS) break;
            boolean matches = entry.symbol().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.koreanName().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.englishName().toLowerCase(Locale.ROOT).contains(needle);
            if (matches) {
                results.add(new CryptoSearchResult(entry.symbol(), entry.koreanName()));
            }
        }
        return results;
    }

    private List<MarketEntry> allMarkets() {
        List<MarketEntry> current = cache.get();
        if (current != null && Instant.now().toEpochMilli() - cachedAt < CACHE_TTL_MILLIS) {
            return current;
        }

        JsonNode body = externalApiRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.upbit.com")
                        .path("/v1/market/all")
                        .queryParam("isDetails", "false")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<MarketEntry> fetched = new ArrayList<>();
        if (body != null && body.isArray()) {
            for (JsonNode node : body) {
                String market = node.path("market").asText();
                if (!market.startsWith("KRW-")) continue;
                fetched.add(new MarketEntry(
                        market.substring("KRW-".length()),
                        node.path("korean_name").asText(),
                        node.path("english_name").asText()
                ));
            }
        }

        cache.set(fetched);
        cachedAt = Instant.now().toEpochMilli();
        return fetched;
    }
}
