package com.nowgnodeel.retirement_planner.asset.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 국내 ETF 마스터 목록을 domestic_stocks에 채운다(is_etf = true).
 *
 * <p>왜 별도 서비스인가: 기존 {@link DomesticStockMasterService}가 쓰는
 * KRX상장종목정보(GetKrxListedInfoService)는 <b>주권만</b> 내려준다 — 실제로 확인해보니
 * 캐시 2,758건 중 KODEX/TIGER가 0건이었다. ETF는 금융위원회_증권상품시세정보
 * (GetSecuritiesProductInfoService)라는 다른 서비스에 있고, data.go.kr은 서비스별로
 * 활용신청을 따로 받기 때문에 같은 키라도 신청 전에는
 * {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}가 돌아온다.
 *
 * <p>활용신청이 승인되면 자동으로 동작한다. 승인 전에는 마스터가 비어 있고 그 상태를
 * 기동 로그로 명확히 알린다 — <b>대표 ETF를 코드에 하드코딩해 두는 방식은 쓰지 않는다.</b>
 * 실제로 그렇게 했다가 20종 중 7종이 코드와 이름이 어긋나 있었다(예: 449180을
 * "TIGER 미국배당다우존스"로 적었으나 실제로는 "KODEX 미국S&P500(H)"). 종목 마스터는
 * 사람이 외워서 적을 값이 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtfMasterService {

    private final DomesticStockRepository domesticStockRepository;
    private final RestClient externalApiRestClient;

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    /** 기동 직후 ETF가 하나도 없으면 한 번 채운다. */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapIfEmpty() {
        try {
            if (domesticStockRepository.countByEtfTrue() > 0) return;
            if (refresh() == 0) {
                log.warn("ETF 마스터가 비어 있고 갱신도 실패했습니다. data.go.kr "
                        + "금융위원회_증권상품시세정보 활용신청 상태를 확인한 뒤 "
                        + "POST /api/admin/etfs/refresh 를 호출하세요. "
                        + "그때까지 연금저축·IRP 계좌에서는 ETF를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.warn("ETF 마스터 초기 적재 실패(무시하고 기동 계속): {}", e.toString());
        }
    }

    @Scheduled(cron = "0 30 3 * * MON")
    public void scheduledRefresh() {
        refresh();
    }

    /**
     * 정식 경로. 활용신청 전에는 응답에 items가 없어 0을 반환한다(예외로 만들지 않는다 —
     * 시세·마스터 조회 실패는 조용히 degrade한다는 이 레포의 기존 정책과 같다).
     */
    @Transactional
    public int refresh() {
        for (int daysBack = 1; daysBack <= 10; daysBack++) {
            String basDt = LocalDate.now().minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE);
            JsonNode items;
            try {
                items = externalApiRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("apis.data.go.kr")
                                .path("/1160100/service/GetSecuritiesProductInfoService/getETFPriceInfo")
                                .queryParam("serviceKey", apiKey)
                                .queryParam("resultType", "json")
                                .queryParam("numOfRows", 3000)
                                .queryParam("basDt", basDt)
                                .build())
                        .retrieve()
                        .body(JsonNode.class)
                        .path("response").path("body").path("items").path("item");
            } catch (Exception e) {
                log.warn("ETF 마스터 조회 실패(basDt={}): {}", basDt, e.toString());
                return 0;
            }

            if (items == null || !items.isArray() || items.isEmpty()) {
                continue; // 휴장일 추정 — 하루 전으로 재시도
            }

            int count = 0;
            for (JsonNode item : items) {
                String symbolCode = item.path("srtnCd").asText(null);
                String name = item.path("itmsNm").asText(null);
                if (symbolCode == null || name == null || symbolCode.isBlank()) continue;
                symbolCode = normalize(symbolCode);
                upsertEtf(symbolCode, name);
                count++;
            }
            log.info("ETF 마스터 {}건 갱신(basDt={})", count, basDt);
            return count;
        }
        log.warn("ETF 마스터: 최근 10일 안에 조회 가능한 데이터가 없습니다.");
        return 0;
    }

    private void upsertEtf(String symbolCode, String name) {
        domesticStockRepository.findById(symbolCode)
                .ifPresentOrElse(
                        existing -> existing.refreshAsEtf(name, existing.getMarket()),
                        () -> domesticStockRepository.save(DomesticStock.builder()
                                .symbolCode(symbolCode)
                                .name(name)
                                .market("KOSPI")   // 국내 ETF는 전부 유가증권시장 상장
                                .etf(true)
                                .build()));
    }

    /** data.go.kr은 종목코드를 'A005930'처럼 접두 A를 붙여 주는 경우가 있다. */
    private String normalize(String symbolCode) {
        String trimmed = symbolCode.trim();
        return trimmed.startsWith("A") ? trimmed.substring(1) : trimmed;
    }
}
