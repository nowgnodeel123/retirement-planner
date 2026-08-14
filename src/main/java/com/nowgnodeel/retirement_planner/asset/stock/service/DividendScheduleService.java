package com.nowgnodeel.retirement_planner.asset.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.dto.DividendScheduleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * D-140/D-148/D-153: 금융위원회_주식배당정보(data.go.kr, GetStocDiviInfoService_V2/getDiviInfo_V2)
 * 실연동 — 종목코드로 OpenDartCorpCodeService에서 법인등록번호(crno)를 얻은 뒤 이 API를 조회한다.
 *
 * ⚠ 개발단계 전용 기능(D-153에서 사용자 확정) — 이 데이터는 공공누리 2유형(출처표시+상업적
 * 이용금지)이라, 네스트가 상업 서비스로 전환되는 시점에는 데이터 원천 소유자인 한국예탁결제원과
 * 별도 정보이용계약이 필요할 수 있다. 실사용자에게 이 기능을 노출하기 전 반드시 라이선스 재검토 필요.
 *
 * DATA_GO_KR_API_KEY가 이 API에 대해 별도 "활용신청" 승인을 받기 전까지는
 * SERVICE_KEY_IS_NOT_REGISTERED_ERROR로 실패하며, D-058 원칙에 따라 Optional.empty()로 흡수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendScheduleService {

    private final OpenDartCorpCodeService openDartCorpCodeService;
    private final RestClient externalApiRestClient;

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    public Optional<DividendScheduleResult> lookupByStockCode(String stockCode) {
        return openDartCorpCodeService.resolveJurirNo(stockCode)
                .flatMap(this::fetchByJurirNo);
    }

    private Optional<DividendScheduleResult> fetchByJurirNo(String jurirNo) {
        try {
            JsonNode item = externalApiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("apis.data.go.kr")
                            .path("/1160100/GetStocDiviInfoService_V2/getDiviInfo_V2")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 1)
                            .queryParam("resultType", "json")
                            .queryParam("crno", jurirNo)
                            .build())
                    .retrieve()
                    .body(JsonNode.class)
                    .path("response").path("body").path("items").path("item");

            if (item.isMissingNode() || (item.isArray() && item.isEmpty())) {
                return Optional.empty();
            }
            JsonNode row = item.isArray() ? item.get(0) : item;

            return Optional.of(new DividendScheduleResult(
                    row.path("dvdnBasDt").asText(null),
                    row.path("cashDvdnPayDt").asText(null),
                    row.path("stckHndvDt").asText(null),
                    row.path("stckIssuCmpyNm").asText(null),
                    row.path("stckGenrDvdnAmt").asText(null),
                    row.path("stckGenrDvdnRt").asText(null)
            ));
        } catch (Exception e) {
            log.warn("금융위원회_주식배당정보 조회 실패 crno={}", jurirNo, e);
            return Optional.empty();
        }
    }
}
