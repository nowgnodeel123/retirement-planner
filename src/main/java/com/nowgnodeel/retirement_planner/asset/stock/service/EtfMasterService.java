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
import java.util.List;

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
 * <p>그래서 이 서비스는 두 경로를 둔다:
 * <ol>
 *   <li>정식 경로 — 위 API를 호출해 전체 ETF를 적재한다. 활용신청이 승인되면 자동으로 동작한다.</li>
 *   <li>부트스트랩 시드 — API를 못 쓰는 동안에도 연금저축 화면이 빈 검색창으로 죽지 않도록,
 *       연금저축·IRP에서 실제로 많이 담는 대표 ETF만 최소한으로 넣어둔다.
 *       <b>전체 목록이 아니다</b> — 정식 경로가 열리면 그대로 덮어써진다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtfMasterService {

    private final DomesticStockRepository domesticStockRepository;
    private final RestClient externalApiRestClient;

    @Value("${price-api.data-go-kr.key}")
    private String apiKey;

    /**
     * 부트스트랩 시드. 정식 API가 열리기 전까지만 쓰이는 최소 목록이라 "전체 ETF"가 아니다.
     * 종목코드는 KRX 상장 코드 기준.
     */
    private static final List<String[]> SEED_ETFS = List.of(
            new String[]{"379800", "KODEX 미국S&P500"},
            new String[]{"379810", "KODEX 미국나스닥100"},
            new String[]{"360750", "TIGER 미국S&P500"},
            new String[]{"133690", "TIGER 미국나스닥100"},
            new String[]{"381180", "TIGER 미국필라델피아반도체나스닥"},
            new String[]{"449180", "TIGER 미국배당다우존스"},
            new String[]{"458730", "TIGER 미국배당다우존스타겟커버드콜2호"},
            new String[]{"069500", "KODEX 200"},
            new String[]{"102110", "TIGER 200"},
            new String[]{"229200", "KODEX 코스닥150"},
            new String[]{"305720", "KODEX 2차전지산업"},
            new String[]{"091160", "KODEX 반도체"},
            new String[]{"148070", "KOSEF 국고채10년"},
            new String[]{"273130", "KODEX 종합채권(AA-이상)액티브"},
            new String[]{"357870", "TIGER CD금리투자KIS"},
            new String[]{"423160", "KODEX 24-12 은행채(AA+이상)액티브"},
            new String[]{"329750", "TIGER 미국MSCI리츠"},
            new String[]{"316140", "KODEX 은행"},
            new String[]{"278530", "KODEX 200TR"},
            new String[]{"294400", "KOSEF 미국달러선물"}
    );

    /** 기동 직후 ETF가 하나도 없으면 한 번 채운다 — 연금저축 화면이 빈 검색으로 시작하지 않도록. */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapIfEmpty() {
        try {
            if (domesticStockRepository.countByEtfTrue() > 0) return;
            int loaded = refresh();
            if (loaded == 0) {
                int seeded = seed();
                log.warn("ETF 정식 API를 쓸 수 없어 부트스트랩 시드 {}건만 적재했습니다. "
                        + "data.go.kr 금융위원회_증권상품시세정보 활용신청 후 "
                        + "POST /api/admin/etfs/refresh 로 전체 목록을 받으세요.", seeded);
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

    @Transactional
    public int seed() {
        for (String[] row : SEED_ETFS) {
            upsertEtf(row[0], row[1]);
        }
        return SEED_ETFS.size();
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
