package com.nowgnodeel.retirement_planner.asset.stock.controller;

import com.nowgnodeel.retirement_planner.asset.stock.dto.DividendScheduleResult;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import com.nowgnodeel.retirement_planner.asset.stock.service.DividendScheduleService;
import com.nowgnodeel.retirement_planner.asset.stock.service.DomesticStockMasterService;
import com.nowgnodeel.retirement_planner.asset.stock.service.EtfMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class DomesticStockController {

    private final DomesticStockRepository domesticStockRepository;
    private final DomesticStockMasterService domesticStockMasterService;
    private final DividendScheduleService dividendScheduleService;
    private final EtfMasterService etfMasterService;

    // 자산 추가 화면 종목검색 자동완성 (국내주식). 이름 길이 오름차순 랭킹으로
    // "삼성" 검색 시 삼성전자 같은 본체 상장사가 지주사·자회사·스팩보다 위로
    // 온다(실제 UX 점검에서 발견해 수정, DomesticStockRepository 주석 참고).
    // etfOnly=true면 ETF만 — 연금저축·IRP는 개별주를 못 사기 때문(D-198).
    @GetMapping("/api/domestic-stocks/search")
    public ResponseEntity<List<DomesticStock>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean etfOnly
    ) {
        PageRequest page = PageRequest.of(0, 20);
        return ResponseEntity.ok(etfOnly
                ? domesticStockRepository.searchEtfByNameOrderByRelevance(keyword, page)
                : domesticStockRepository.searchByNameOrderByRelevance(keyword, page));
    }

    // ETF 마스터 적재/갱신. data.go.kr 증권상품시세정보 활용신청이 승인되면 이걸로 전체를 받는다.
    @PostMapping("/api/admin/etfs/refresh")
    public ResponseEntity<String> refreshEtfs() {
        int count = etfMasterService.refresh();
        if (count == 0) {
            int seeded = etfMasterService.seed();
            return ResponseEntity.ok(
                    "정식 ETF API를 쓸 수 없어 부트스트랩 시드 " + seeded + "건만 적재했습니다. "
                            + "data.go.kr 금융위원회_증권상품시세정보 활용신청이 필요합니다.");
        }
        return ResponseEntity.ok(count + "건 갱신 완료");
    }

    // 초기 적재 / 급한 갱신용 수동 트리거
    @PostMapping("/api/admin/domestic-stocks/refresh")
    public ResponseEntity<String> refresh() {
        int count = domesticStockMasterService.refresh();
        return ResponseEntity.ok(count + "건 갱신 완료");
    }

    // D-148/D-153: 개발단계 검증용 — 아직 프론트 미연결, 라이선스 재검토 전까지 실사용자 노출 금지
    @GetMapping("/api/admin/domestic-stocks/{symbolCode}/dividend-schedule")
    public ResponseEntity<DividendScheduleResult> dividendSchedule(@PathVariable String symbolCode) {
        Optional<DividendScheduleResult> result = dividendScheduleService.lookupByStockCode(symbolCode);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}