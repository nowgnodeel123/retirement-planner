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
    private static final int MAX_LOOKBACK_DAYS = 10;

    /** 특정 날짜의 매매기준율 조회 결과 — 역탐색으로 인해 baseDate가 요청일과 다를 수 있다. */
    public record DatedRate(BigDecimal dealBasR, LocalDate baseDate) {}

    // 영업일 11:30(KST) 이후 갱신 시도. zone 명시로 배포 서버 타임존(Railway는 기본 UTC일 수 있음) 영향 배제.
    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledRefresh() {
        refresh();
    }

    public Optional<ExchangeRate> getRate(String currencyCode) {
        return exchangeRateRepository.findById(currencyCode);
    }

    /**
     * 특정 거래일 기준 매매기준율을 조회한다(D-087 유지 — DB에 저장하지 않고 요청 시점에 프록시 조회만).
     * 주말·휴장일이면 직전 영업일로 최대 {@value #MAX_LOOKBACK_DAYS}일 역탐색한다(국내주식 역탐색 R-012와 동일 원칙).
     * 해외주식 매수/매도·배당 폼에서 사용자가 환율을 손으로 입력하지 않도록 기본값을 채우는 용도.
     */
    public Optional<DatedRate> getRateOn(String currencyCode, LocalDate date) {
        if (!TARGET_CURRENCY.equals(currencyCode)) {
            return Optional.empty();
        }
        for (int daysBack = 0; daysBack <= MAX_LOOKBACK_DAYS; daysBack++) {
            LocalDate searchDate = date.minusDays(daysBack);
            Optional<BigDecimal> rate = fetchDealBasR(searchDate);
            if (rate.isPresent()) {
                return Optional.of(new DatedRate(rate.get(), searchDate));
            }
        }
        log.warn("{} 기준 최근 {}일 내 유효한 {} 환율을 찾지 못함", date, MAX_LOOKBACK_DAYS, currencyCode);
        return Optional.empty();
    }

    @Transactional
    public int refresh() {
        // 당일(daysBack=0)부터 시도 — 11시 이전 요청이면 null 반환되므로 하루 전으로 재시도.
        for (int daysBack = 0; daysBack <= MAX_LOOKBACK_DAYS; daysBack++) {
            LocalDate searchDate = LocalDate.now().minusDays(daysBack);
            Optional<BigDecimal> rate = fetchDealBasR(searchDate);
            if (rate.isEmpty()) continue;

            ExchangeRate entity = exchangeRateRepository.findById(TARGET_CURRENCY)
                    .orElseGet(() -> ExchangeRate.builder()
                            .currencyCode(TARGET_CURRENCY)
                            .dealBasR(rate.get())
                            .baseDate(searchDate)
                            .build());
            entity.refresh(rate.get(), searchDate);
            exchangeRateRepository.save(entity);

            log.info("환율 갱신 완료: {}={} (기준일={})", TARGET_CURRENCY, rate.get(), searchDate);
            return 1;
        }

        log.warn("최근 {}일 내 유효한 {} 환율 데이터를 찾지 못함", MAX_LOOKBACK_DAYS, TARGET_CURRENCY);
        return 0;
    }

    /**
     * 한국수출입은행 오픈API에서 특정 날짜의 USD 매매기준율 한 건을 읽는다.
     * 비영업일 또는 11시 이전 요청이면 빈 배열이 오므로 {@code Optional.empty()}.
     */
    private Optional<BigDecimal> fetchDealBasR(LocalDate searchDate) {
        String searchDateStr = searchDate.format(DateTimeFormatter.BASIC_ISO_DATE);

        JsonNode root = externalApiRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("oapi.koreaexim.go.kr")
                        .path("/site/program/financial/exchangeJSON")
                        .queryParam("authkey", apiKey)
                        .queryParam("searchdate", searchDateStr)
                        .queryParam("data", "AP01")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        // 비영업일 또는 11시 이전 요청 시 null 반환(수출입은행 API 명세 "이용시 유의사항")
        if (root == null || !root.isArray() || root.isEmpty()) {
            log.info("searchdate={} 환율 데이터 없음(비영업일 또는 11시 이전 추정)", searchDateStr);
            return Optional.empty();
        }

        for (JsonNode item : root) {
            if (item.path("result").asInt() != 1) continue;
            if (!TARGET_CURRENCY.equals(item.path("cur_unit").asText())) continue;

            // deal_bas_r는 "1,066.9"처럼 콤마 포함 문자열로 옴 — 제거 후 파싱 필수
            String raw = item.path("deal_bas_r").asText().replace(",", "");
            return Optional.of(new BigDecimal(raw));
        }
        log.info("searchdate={} 응답에 {} 항목 없음", searchDateStr, TARGET_CURRENCY);
        return Optional.empty();
    }
}
