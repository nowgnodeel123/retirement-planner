package com.nowgnodeel.retirement_planner.asset.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowgnodeel.retirement_planner.asset.stock.entity.CorpCode;
import com.nowgnodeel.retirement_planner.asset.stock.repository.CorpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

/**
 * D-148: 배당 ex-date 자동조회(D-140)의 선행 단계. OpenDART(금융감독원 전자공시시스템, data.go.kr과
 * 별도 시스템)의 corpCode.xml(전체 상장사 종목코드→고유번호 매핑, ZIP)을 내려받아 로컬에 캐싱하고,
 * 종목코드로 법인등록번호(jurir_no)를 lazy하게 조회한다. jurir_no까지만 제공 — 이걸로 실제 배당
 * ex-date를 조회하는 data.go.kr "금융위원회_주식배당정보" 연동은 필드 스펙 미확인으로 별도 진행(다음 행동 참조).
 *
 * OPENDART_API_KEY 환경변수가 없으면 모든 메서드가 Optional.empty()/no-op으로 조용히 흡수한다
 * (D-058과 동일한 예외 흡수 원칙 — 아직 키가 없는 게 정상 상태, 에러가 아니다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDartCorpCodeService {

    private final CorpCodeRepository corpCodeRepository;
    private final RestClient bulkDownloadRestClient;
    private final RestClient externalApiRestClient;

    @Value("${opendart.api-key:}")
    private String apiKey;

    private boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Scheduled(cron = "0 0 4 * * MON")
    public void scheduledRefresh() {
        refresh();
    }

    /** corpCode.xml 전체를 내려받아 stock_code→corp_code/corp_name 매핑을 갱신한다(jurir_no는 건드리지 않음). */
    @Transactional
    public int refresh() {
        if (!isEnabled()) {
            log.debug("OPENDART_API_KEY 미설정 — corpCode 갱신 생략");
            return 0;
        }

        byte[] zip;
        try {
            zip = bulkDownloadRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("opendart.fss.or.kr")
                            .path("/api/corpCode.xml")
                            .queryParam("crtfc_key", apiKey)
                            .build())
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.warn("OpenDART corpCode.xml 다운로드 실패", e);
            return 0;
        }

        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            if (zis.getNextEntry() == null) {
                log.warn("OpenDART corpCode.zip 안에 항목이 없음(키 오류 등으로 에러 XML만 왔을 가능성)");
                return 0;
            }
            ByteArrayOutputStream xmlBytes = new ByteArrayOutputStream();
            zis.transferTo(xmlBytes);

            for (Element list : parseListElements(xmlBytes.toByteArray())) {
                String stockCode = text(list, "stock_code");
                String corpCode = text(list, "corp_code");
                String corpName = text(list, "corp_name");
                if (stockCode == null || stockCode.isBlank() || corpCode == null) continue; // 비상장사는 stock_code 공백

                CorpCode entity = corpCodeRepository.findById(stockCode)
                        .orElseGet(() -> CorpCode.builder()
                                .stockCode(stockCode)
                                .corpCode(corpCode)
                                .corpName(corpName)
                                .build());
                entity.refresh(corpCode, corpName);
                corpCodeRepository.save(entity);
                count++;
            }
        } catch (Exception e) {
            log.warn("OpenDART corpCode.xml 파싱 실패", e);
            return count;
        }

        log.info("OpenDART corp_codes 갱신 완료: {}건", count);
        return count;
    }

    /** 종목코드로 법인등록번호를 조회한다. 로컬 매핑에 없거나 API 실패 시 empty. */
    @Transactional
    public Optional<String> resolveJurirNo(String stockCode) {
        if (!isEnabled()) return Optional.empty();

        return corpCodeRepository.findById(stockCode).flatMap(entity -> {
            if (entity.getJurirNo() != null && !entity.getJurirNo().isBlank()) {
                return Optional.of(entity.getJurirNo());
            }
            return fetchJurirNo(entity.getCorpCode())
                    .map(jurirNo -> {
                        entity.cacheJurirNo(jurirNo);
                        return jurirNo;
                    });
        });
    }

    private Optional<String> fetchJurirNo(String corpCode) {
        try {
            JsonNode response = externalApiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("opendart.fss.or.kr")
                            .path("/api/company.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !"000".equals(response.path("status").asText())) {
                log.warn("OpenDART 기업개황 조회 실패 corp_code={} status={}",
                        corpCode, response == null ? null : response.path("status").asText());
                return Optional.empty();
            }
            String jurirNo = response.path("jurir_no").asText(null);
            return (jurirNo == null || jurirNo.isBlank()) ? Optional.empty() : Optional.of(jurirNo);
        } catch (Exception e) {
            log.warn("OpenDART 기업개황 조회 실패 corp_code={}", corpCode, e);
            return Optional.empty();
        }
    }

    private static List<Element> parseListElements(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 방지
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;
        try (InputStream is = new ByteArrayInputStream(xml)) {
            doc = builder.parse(is);
        }
        NodeList nodes = doc.getElementsByTagName("list");
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    private static String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) return null;
        return nodes.item(0).getTextContent().trim();
    }
}
