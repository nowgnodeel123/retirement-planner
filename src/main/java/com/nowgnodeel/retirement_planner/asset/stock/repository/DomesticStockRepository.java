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
    @Query("SELECT s FROM DomesticStock s WHERE s.name LIKE %:keyword% " +
            "ORDER BY LENGTH(s.name) ASC, s.name ASC")
    List<DomesticStock> searchByNameOrderByRelevance(@Param("keyword") String keyword, Pageable pageable);
}