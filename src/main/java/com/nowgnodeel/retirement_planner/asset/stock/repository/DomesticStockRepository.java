package com.nowgnodeel.retirement_planner.asset.stock.repository;

import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DomesticStockRepository extends JpaRepository<DomesticStock, String> {

    // 종목검색 자동완성용 — 이름 길이 오름차순(짧을수록 상위) 후 가나다순.
    // WHY: 시가총액·거래량 데이터가 없어 "관련도" 자체를 계산할 수 없다. 그런데
    // 알파벳(가나다)순만 쓰면 "삼성" 검색 시 삼성전자가 삼성바이오로직스·
    // 삼성에스디에스·삼성에피스홀딩스보다 뒤로 밀려 스크롤 없이는 안 보였다
    // (실제 UX 점검에서 발견). 지주사·스팩·자회사는 정식 사명이 길어지는 경향이
    // 있어 이름 길이가 "본체 상장사일 가능성"의 대리 신호로 쓸만하다 — 완벽한
    // 랭킹은 아니지만 이 케이스를 포함해 실사용 빈도가 높은 종목을 앞으로 당긴다.
    //
    // 종목명뿐 아니라 종목코드(symbolCode)로도 찾는다 — "005930"처럼 코드를 아는
    // 사용자가 적지 않은데 이름만 보고 있어서 0건이 나왔다.
    // 검색어와 종목명을 양쪽 다 소문자화 + 공백 제거해서 비교한다(실기기 QA에서 발견).
    // Postgres LIKE는 대소문자를 구분해 "kodex"가 0건이었고, 프론트의 trim()은 앞뒤만
    // 다듬으므로 "삼성 전자"처럼 중간에 띄어쓴 입력도 0건이었다. 어차피 선행 와일드카드라
    // 인덱스를 못 타고 전체 스캔인데다 마스터가 4천 건 수준이라 함수 적용 비용은 무시할 만하다.
    @Query("SELECT s FROM DomesticStock s " +
            "WHERE LOWER(REPLACE(s.name, ' ', '')) LIKE LOWER(CONCAT('%', REPLACE(:keyword, ' ', ''), '%')) " +
            "   OR s.symbolCode LIKE CONCAT('%', REPLACE(:keyword, ' ', ''), '%') " +
            "ORDER BY LENGTH(s.name) ASC, s.name ASC")
    List<DomesticStock> searchByNameOrderByRelevance(@Param("keyword") String keyword, Pageable pageable);

    // 연금저축·IRP 자산 추가 화면 전용 — 같은 랭킹 규칙에 ETF 조건만 더한다(D-198).
    @Query("SELECT s FROM DomesticStock s WHERE s.etf = true " +
            "AND (LOWER(REPLACE(s.name, ' ', '')) LIKE LOWER(CONCAT('%', REPLACE(:keyword, ' ', ''), '%')) " +
            "     OR s.symbolCode LIKE CONCAT('%', REPLACE(:keyword, ' ', ''), '%')) " +
            "ORDER BY LENGTH(s.name) ASC, s.name ASC")
    List<DomesticStock> searchEtfByNameOrderByRelevance(@Param("keyword") String keyword, Pageable pageable);

    long countByEtfTrue();
}