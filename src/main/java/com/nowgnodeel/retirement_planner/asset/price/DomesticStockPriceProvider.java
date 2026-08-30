package com.nowgnodeel.retirement_planner.asset.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticStockPriceProvider implements PriceProvider {

    private final RestClient externalApiRestClient;
    private final DomesticStockRepository domesticStockRepository;

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    @Override
    @CircuitBreaker(name = "domesticStockPrice", fallbackMethod = "fallback")
    public BigDecimal getCurrentPrice(String symbolCode) {
        // KRX상장종목정보는 'A' 접두사 포함(A005930), 주식시세정보는 접두사 없는 6자리(005930)로 추정됨
        String queryCode = symbolCode.startsWith("A") ? symbolCode.substring(1) : symbolCode;

        // ETF는 주식시세정보(GetStockSecuritiesInfoService)에 아예 없다 — 실제 호출로 확인했다
        // (069500 KODEX 200을 여러 영업일로 조회해도 totalCount=0, 005930 삼성전자는 정상).
        // 증권상품시세정보라는 다른 서비스를 써야 하고, data.go.kr은 서비스별로 활용신청을 받는다.
        boolean isEtf = domesticStockRepository.findById(queryCode)
                .map(DomesticStock::isEtf)
                .orElse(false);
        String path = isEtf
                ? "/1160100/service/GetSecuritiesProductInfoService/getETFPriceInfo"
                : "/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo";

        for (int daysBack = 1; daysBack <= 10; daysBack++) {
            String basDt = LocalDate.now().minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE);

            JsonNode items = externalApiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("apis.data.go.kr")
                            .path(path)
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
                if (!queryCode.equals(respCode) && !symbolCode.equals(respCode)) {
                    continue;
                }
                // clpr(종가)이 거래정지 등으로 빈 값일 수 있음 — get()은 필드 부재 시 null을 반환해
                // asText() 호출 시 NPE로 이어지고, 이 예외가 상위 PriceService에서 통째로 흡수되면서
                // "간헐적 null" 증상으로만 보이던 원인이었음. path()로 안전하게 읽고 빈 값이면 이전 영업일로 폴백.
                String clpr = item.path("clpr").asText();
                if (!clpr.isBlank()) {
                    return new BigDecimal(clpr);
                }
                log.info("basDt={} symbol={} 종가(clpr) 값 없음(거래정지 추정), 이전 영업일 재탐색", basDt, symbolCode);
            }
            log.info("basDt={} symbol={}(query={}) 유효한 시세 없음", basDt, symbolCode, queryCode);
        }
        throw new IllegalStateException(
                (isEtf ? "ETF" : "국내주식") + " 시세 조회 실패(최근 10일 내 데이터 없음): " + symbolCode);
    }

    private BigDecimal fallback(String symbolCode, Throwable t) {
        throw new IllegalStateException("국내주식 시세 조회 실패(서킷 오픈 또는 하위 예외): " + symbolCode, t);
    }
}