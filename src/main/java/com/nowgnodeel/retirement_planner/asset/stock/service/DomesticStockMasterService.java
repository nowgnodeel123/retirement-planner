package com.nowgnodeel.retirement_planner.asset.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * KRX상장종목정보(금융위원회, data.go.kr)로 전체 상장종목 목록을 받아
 * domestic_stocks 테이블을 갱신한다. 매주 월요일 새벽 스케줄 + 수동 트리거 병행.
 * 근거: data.go.kr에 종목명 자동완성 API가 없음을 확인(M4 착수 전 검증 완료).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockMasterService {

    private final DomesticStockRepository domesticStockRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    private static final String KRX_LISTED_STOCK_ENDPOINT =
            "https://apis.data.go.kr/1160100/service/GetKrxListedInfoService/getItemInfo";

    @Scheduled(cron = "0 0 3 * * MON")
    public void scheduledRefresh() {
        refresh();
    }

    @Transactional
    public int refresh() {
        // 주말/공휴일에는 해당일 데이터가 없으므로, 데이터가 나올 때까지 최대 10일 전까지 거슬러 탐색
        for (int daysBack = 1; daysBack <= 10; daysBack++) {
            String basDt = LocalDate.now().minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE);

            JsonNode items = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("apis.data.go.kr")
                            .path("/1160100/service/GetKrxListedInfoService/getItemInfo")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("resultType", "json")
                            .queryParam("numOfRows", 5000)
                            .queryParam("basDt", basDt)
                            .build())
                    .retrieve()
                    .body(JsonNode.class)
                    .path("response").path("body").path("items").path("item");

            if (!items.isArray() || items.isEmpty()) {
                log.info("basDt={} 데이터 없음(휴장일 추정), 하루 전으로 재시도", basDt);
                continue;
            }

            int count = 0;
            for (JsonNode item : items) {
                String symbolCode = item.path("srtnCd").asText(null);
                String name = item.path("itmsNm").asText(null);
                String market = item.path("mrktCtg").asText("KOSPI");

                if (symbolCode == null || name == null) continue;

                DomesticStock stock = domesticStockRepository.findById(symbolCode)
                        .orElseGet(() -> DomesticStock.builder()
                                .symbolCode(symbolCode)
                                .name(name)
                                .market(market)
                                .build());
                stock.refresh(name, market);
                domesticStockRepository.save(stock);
                count++;
            }

            log.info("국내 종목 마스터 갱신 완료: {}건 (기준일 basDt={})", count, basDt);
            return count;
        }

        log.warn("최근 10일 내 유효한 거래일 데이터를 찾지 못함");
        return 0;
    }
}