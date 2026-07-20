package com.nowgnodeel.retirement_planner.asset.fx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final RestClient externalApiRestClient;

    @Value("${price-api.koreaexim.key}")
    private String apiKey;

    // MVP는 USD만 사용(D-063 스코프: 해외주식 원화환산). 여러 통화 확장은 Phase 2.
    private static final String TARGET_CURRENCY = "USD";

    // 영업일 11:30(KST) 이후 갱신 시도. zone 명시로 배포 서버 타임존(Railway는 기본 UTC일 수 있음) 영향 배제.
    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledRefresh() {
        refresh();
    }

    public Optional<ExchangeRate> getRate(String currencyCode) {
        return exchangeRateRepository.findById(currencyCode);
    }

    @Transactional
    public int refresh() {
        // 당일(daysBack=0)부터 시도 — 11시 이전 요청이면 null 반환되므로 하루 전으로 재시도.
        // 국내주식 역탐색(R-012)과 동일 원칙, 시작점만 0(당일 우선)으로 차이.
        for (int daysBack = 0; daysBack <= 10; daysBack++) {
            String searchDate = LocalDate.now().minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE);

            JsonNode root = externalApiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("oapi.koreaexim.go.kr")
                            .path("/site/program/financial/exchangeJSON")
                            .queryParam("authkey", apiKey)
                            .queryParam("searchdate", searchDate)
                            .queryParam("data", "AP01")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            // 비영업일 또는 11시 이전 요청 시 null 반환(수출입은행 API 명세 "이용시 유의사항")
            if (root == null || !root.isArray() || root.isEmpty()) {
                log.info("searchdate={} 환율 데이터 없음(비영업일 또는 11시 이전 추정), 하루 전으로 재시도", searchDate);
                continue;
            }

            for (JsonNode item : root) {
                if (item.path("result").asInt() != 1) continue;
                if (!TARGET_CURRENCY.equals(item.path("cur_unit").asText())) continue;

                // deal_bas_r는 "1,066.9"처럼 콤마 포함 문자열로 옴 — 제거 후 파싱 필수
                String raw = item.path("deal_bas_r").asText().replace(",", "");
                BigDecimal rate = new BigDecimal(raw);
                LocalDate baseDate = LocalDate.parse(searchDate, DateTimeFormatter.BASIC_ISO_DATE);

                ExchangeRate entity = exchangeRateRepository.findById(TARGET_CURRENCY)
                        .orElseGet(() -> ExchangeRate.builder()
                                .currencyCode(TARGET_CURRENCY)
                                .dealBasR(rate)
                                .baseDate(baseDate)
                                .build());
                entity.refresh(rate, baseDate);
                exchangeRateRepository.save(entity);

                log.info("환율 갱신 완료: {}={} (기준일={})", TARGET_CURRENCY, rate, baseDate);
                return 1;
            }
            log.info("searchdate={} 응답에 {} 항목 없음", searchDate, TARGET_CURRENCY);
        }

        log.warn("최근 10일 내 유효한 {} 환율 데이터를 찾지 못함", TARGET_CURRENCY);
        return 0;
    }
}