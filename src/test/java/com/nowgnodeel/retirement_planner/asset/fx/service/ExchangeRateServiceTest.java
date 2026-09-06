package com.nowgnodeel.retirement_planner.asset.fx.service;

import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;

/**
 * 2026-09-06에 환율이 52일간(2026-07-16 고정) 갱신되지 않은 채로 발견됐다.
 * 갱신이 "평일 11:30 단 한 번"에만 걸려 있어 그 순간 앱이 떠 있지 않으면 그날은
 * 영영 갱신되지 않는 구조였고, 그동안 해외자산 원화환산이 전부 두 달 전 환율로 계산됐다.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private RestClient externalApiRestClient;

    @InjectMocks private ExchangeRateService exchangeRateService;

    private ExchangeRate rateOn(LocalDate baseDate) {
        return ExchangeRate.builder()
                .currencyCode("USD")
                .dealBasR(new BigDecimal("1400"))
                .baseDate(baseDate)
                .build();
    }

    @Test
    @DisplayName("오늘자 환율을 이미 갖고 있으면 외부 API를 부르지 않는다")
    void skipsWhenTodayRateAlreadyStored() {
        given(exchangeRateRepository.findById("USD"))
                .willReturn(Optional.of(rateOn(LocalDate.now())));

        exchangeRateService.refreshIfStale();

        then(externalApiRestClient).should(never()).get();
    }

    @Test
    @DisplayName("환율이 오래됐으면 갱신을 시도한다")
    void refreshesWhenStoredRateIsStale() {
        given(exchangeRateRepository.findById("USD"))
                .willReturn(Optional.of(rateOn(LocalDate.now().minusDays(52))));

        exchangeRateService.refreshIfStale();

        then(externalApiRestClient).should(atLeastOnce()).get();
    }

    @Test
    @DisplayName("환율이 아예 없어도 갱신을 시도한다(최초 기동)")
    void refreshesWhenNothingStored() {
        given(exchangeRateRepository.findById("USD")).willReturn(Optional.empty());

        exchangeRateService.refreshIfStale();

        then(externalApiRestClient).should(atLeastOnce()).get();
    }

    @Test
    @DisplayName("외부 API가 죽어 있어도 예외를 밖으로 던지지 않는다 — 기동과 스케줄러가 죽으면 안 된다")
    void swallowsFailureSoStartupAndSchedulerSurvive() {
        given(exchangeRateRepository.findById("USD"))
                .willThrow(new RuntimeException("DB 장애"));

        // 예외가 밖으로 새면 ApplicationReadyEvent 리스너가 기동을 깨뜨린다.
        exchangeRateService.refreshIfStale();
    }
}
