package com.nowgnodeel.retirement_planner.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 API(시세/환율) 호출 공용 RestClient.
 * 타임아웃 미설정 시 외부 API 지연이 보유자산 조회 전체를 붙잡는 문제 방지 —
 * 실패는 D-058 예외흡수/캐시 폴백으로 처리되므로 짧게 끊는 게 낫다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient externalApiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * OpenDART corpCode.xml(ZIP, 전체 상장사 목록) 같은 대용량 일회성 다운로드 전용.
     * 개별 요청을 짧게 끊는 externalApiRestClient와 달리 스케줄 배치 성격이라 여유를 둔다.
     */
    @Bean
    public RestClient bulkDownloadRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}