package com.nowgnodeel.retirement_planner.asset.price;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class DomesticStockPriceProvider implements PriceProvider {

    private final RestClient restClient = RestClient.create();

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    @Override
    public BigDecimal getCurrentPrice(String symbolCode) {
        // KRX상장종목정보는 'A' 접두사 포함(A005930), 주식시세정보는 접두사 없는 6자리(005930)로 추정됨
        String queryCode = symbolCode.startsWith("A") ? symbolCode.substring(1) : symbolCode;

        for (int daysBack = 1; daysBack <= 10; daysBack++) {
            String basDt = LocalDate.now().minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE);

            JsonNode items = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("apis.data.go.kr")
                            .path("/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("resultType", "json")
                            .queryParam("numOfRows", 10)
                            .queryParam("basDt", basDt)
                            .queryParam("likeSrtnCd", queryCode)
                            .build())
                    .retrieve()
                    .body(JsonNode.class)
                    .path("response").path("body").path("items").path("item");

            if (!items.isArray() || items.isEmpty()) {
                log.info("basDt={} 시세 없음(휴장일 추정), symbol={}", basDt, symbolCode);
                continue;
            }

            for (JsonNode item : items) {
                String respCode = item.path("srtnCd").asText();
                if (queryCode.equals(respCode) || symbolCode.equals(respCode)) {
                    return new BigDecimal(item.get("clpr").asText());
                }
            }
            log.info("basDt={} symbol={}(query={}) 정확히 일치하는 종목 없음", basDt, symbolCode, queryCode);
        }
        throw new IllegalStateException("국내주식 시세 조회 실패(최근 10일 내 데이터 없음): " + symbolCode);
    }
}