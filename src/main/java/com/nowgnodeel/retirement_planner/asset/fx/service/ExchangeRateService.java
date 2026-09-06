package com.nowgnodeel.retirement_planner.asset.fx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    /**
     * 영업일 11:30~18:30(KST) 매시 갱신 시도. zone 명시로 배포 서버 타임존(Railway는 기본 UTC일 수 있음) 영향 배제.
     *
     * WHY 한 번이 아니라 매시인가: 예전에는 평일 11:30 한 번뿐이라 그 순간 앱이 떠 있지 않으면
     * 그날은 영영 갱신되지 않았다. 로컬 개발처럼 필요할 때만 띄우는 환경에서는 사실상 한 번도
     * 안 돌아서 환율이 52일간 멈춰 있었고(2026-09-06 확인), 그동안 해외자산 원화환산이 전부
     * 두 달 전 환율로 계산됐다. 배포 후에도 재시작·일시 장애가 같은 구멍을 만든다.
     *
     * 이미 오늘자 환율을 갖고 있으면 건너뛰므로(refreshIfStale) 실제 외부 호출은 하루 한 번이다.
     */
    @Scheduled(cron = "0 30 11-18 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledRefresh() {
        refreshIfStale();
    }

    /**
     * 기동 직후에도 한 번 확인한다. 스케줄만 있으면 "앱이 떠 있는 시각"에 갱신이 의존하는데,
     * 배포·재시작·로컬 실행은 그 시각을 피해 가는 일이 훨씬 많다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refreshIfStale();
    }

    /**
     * 오늘자 환율을 이미 갖고 있으면 아무것도 하지 않는다 — 그보다 최신인 값은 존재할 수 없다.
     * 그 외에는 갱신을 시도하되, 실패가 스케줄러나 기동을 깨뜨리지 않도록 흡수한다.
     * 환율은 없으면 화면이 degrade할 뿐이고(D-058), 여기서 예외를 터뜨려 얻을 게 없다.
     */
    public void refreshIfStale() {
        try {
            boolean hasTodayRate = exchangeRateRepository.findById(TARGET_CURRENCY)
                    .map(rate -> !rate.getBaseDate().isBefore(LocalDate.now()))
                    .orElse(false);
            if (hasTodayRate) {
                return;
            }
            refresh();
        } catch (Exception e) {
            log.warn("환율 자동 갱신 실패 — 기존 값을 유지한다", e);
        }
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
