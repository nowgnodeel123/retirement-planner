// asset/tax/dto/TaxDtos.java
package com.nowgnodeel.retirement_planner.asset.tax.dto;

import java.math.BigDecimal;

public class TaxDtos {

    // D-068: 배당소득세는 실제 세액을 계산하지 않고 신고 방식 판정만 제공한다.
    public enum DividendTaxJudgement { SEPARATE_TAXATION_FINAL, COMPREHENSIVE_FILING_POSSIBLE }

    /**
     * M11(D-064/D-068): 계좌 스코프 세금 탭 — 귀속연도 단위 양도소득세 추정 + 배당소득세 판정.
     * 세금계산기 완전판 금지(0장 절대원칙) — 두 값 모두 추정/판정이며 실제 세액이 아니다.
     */
    public record TaxSummaryResponse(
            int year,
            CapitalGainsEstimate capitalGains,
            DividendIncomeJudgement dividendIncome
    ) {}

    /**
     * D-064: 해외주식 연간 실현손익 기준 양도소득세 "추정치"만 제공. 국내주식은 대상에서 완전 제외.
     * 세율(20% 국세+2% 지방세)·기본공제(250만원)는 세법상 고정값을 그대로 적용한 것으로,
     * 임의 추정·보간이 아니다(13장 절대원칙 대상은 시세·환율 데이터 보간 금지이지 세율 적용이 아님).
     */
    public record CapitalGainsEstimate(
            BigDecimal realizedProfitKrw,   // 해외주식 연간 실현손익 합(D-107, 손실 포함 원값)
            BigDecimal basicDeductionKrw,   // 250만원 고정
            BigDecimal taxableBaseKrw,      // max(0, realizedProfitKrw - basicDeductionKrw)
            BigDecimal taxRate,             // 0.22 고정
            BigDecimal estimatedTaxKrw,     // taxableBaseKrw * taxRate
            int sellCount
    ) {}

    /**
     * D-068: 실제 종합소득세액은 계산하지 않고, 금융소득 2천만원 기준 분리과세 종결 여부만 판정한다.
     * interestIncomeNotTracked: 이 앱은 예적금 이자(D-060)를 추적하지 않아 실제 금융소득이
     * totalDividendKrw보다 클 수 있다는 캐비트 — 프론트에 상시 노출 필요.
     * R-016 보완: totalDividendKrw는 국내주식 배당(저장값=세후 순액)을 원천징수율 15.4%로
     * 세전 역환산한 뒤 해외주식(저장값=세전 USD→원화 환산)과 합산한 값이다. 실제 세전 금액과
     * 다를 수 있는 추정치이므로 dividendGrossedUp을 프론트에서 함께 안내한다.
     */
    public record DividendIncomeJudgement(
            BigDecimal totalDividendKrw,    // 연간 배당 합 추정(세전 환산치, 국내는 15.4% 역환산 적용)
            BigDecimal thresholdKrw,        // 2000만원 고정
            boolean exceedsThreshold,
            DividendTaxJudgement judgement,
            boolean interestIncomeNotTracked, // 항상 true — 이자소득 미추적 캐비트
            boolean dividendGrossedUp,        // 항상 true — 국내주식 배당 세전 역환산 적용 캐비트(R-016)
            int dividendCount
    ) {}
}
