package com.nowgnodeel.retirement_planner.asset.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * D-148: 배당 ex-date 자동조회(D-140)의 선행 단계 — 종목코드(stock_code)로부터
 * OpenDART 고유번호(corp_code)를 거쳐 법인등록번호(jurir_no)를 얻기 위한 로컬 캐시.
 * jurir_no는 OpenDartCorpCodeService.resolveJurirNo()가 최초 조회 시점에 lazy하게 채운다
 * (corpCode.xml 일괄 다운로드에는 포함되지 않고 기업개황 API를 종목별로 별도 호출해야 함).
 */
@Entity
@Table(name = "corp_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpCode {

    @Id
    @Column(name = "stock_code", length = 10)
    private String stockCode;

    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName;

    @Column(name = "jurir_no", length = 13)
    private String jurirNo;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CorpCode(String stockCode, String corpCode, String corpName) {
        this.stockCode = stockCode;
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.updatedAt = LocalDateTime.now();
    }

    public void refresh(String corpCode, String corpName) {
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.updatedAt = LocalDateTime.now();
    }

    public void cacheJurirNo(String jurirNo) {
        this.jurirNo = jurirNo;
        this.updatedAt = LocalDateTime.now();
    }
}
