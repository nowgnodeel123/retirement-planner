package com.nowgnodeel.retirement_planner.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SimulationResponseDto {

    private Summary summary;
    private Breakdown breakdown;
    private AccumulatedAssets accumulatedAssets;
    private TaxDetail taxDetail;
    private TaxBenefit taxBenefit;
    private DependentStatusWarning dependentStatusWarning;
    private MonteCarloResult monteCarloResult;
    private Meta meta;

    // 은퇴 시점~90세까지 연도별 소득 구성. 결과 화면 차트용.
    private List<YearlyIncomePoint> incomeTimeline;

    @Getter @Builder
    public static class Summary {
        private long totalMonthlyIncome;
        private long totalMonthlyIncomeGross;
        private long targetMonthlyExpense;
        // 위 targetMonthlyExpense는 사용자가 입력한 "오늘 기준" 금액이고, 이 값은 그것을
        // 은퇴 시점까지 물가상승(2.5%)으로 환산한 금액이다. totalMonthlyIncome이 명목이라
        // 비교는 반드시 이쪽과 해야 한다 — 예전에는 명목 소득에서 오늘 기준 목표를 빼서
        // "월 693만원 여유"처럼 실제로 없는 여유가 표시됐다.
        private long targetMonthlyExpenseAtRetirement;
        private long monthlyShortfall;
        private int estimatedRetirementAge;
        // WHY: 75세까지도 목표를 못 채우는 케이스를 프론트가 구분해서
        // 축하 톤 대신 경고 톤 UI를 보여줄 수 있게 한다. (검토 Q-2/U-1)
        private boolean feasible;
        private String message;
        private String shareMessage;
    }

    @Getter @Builder
    public static class Breakdown {
        private long nationalPension;
        private long retirementPension;
        private long retirementPensionGross;
        private long irp;
        private long irpGross;
        private long pensionSavings;
        private long pensionSavingsGross;
        private long pensionSavingsTaxBenefit;
        private long stockAsset;
    }

    /**
     * 은퇴 시점에 모여 있는 자산(만원). 월 수령액만으로는 규모가 안 잡혀서 함께 내려보낸다 —
     * "그 자산이 은퇴 시점에 충분한가"(D-017)가 이 앱의 질문인데 정작 총액이 없었다.
     *
     * pensionUnlockAge: 연금 계열(퇴직연금·IRP·연금저축) 잔액의 기준 나이다. 55세 전에
     * 은퇴하면 연금은 55세까지 더 굴러가므로 주식 잔액(은퇴 시점)과 기준 시점이 다르다.
     */
    @Getter @Builder
    public static class AccumulatedAssets {
        private long retirementPensionLumpSum;
        private long irpBalance;
        private long pensionSavingsBalance;
        private long liquidBalance;
        private long total;
        private int pensionUnlockAge;
    }

    @Getter @Builder
    public static class TaxDetail {
        private double pensionIncomeTaxRate;
        private double healthInsuranceRate;
        private long monthlyPensionTax;
        private long monthlyHealthInsurance;
        // 은퇴 1년차 주식/ETF 인출분에 부과되는 양도소득세(22%, 연 250만원 공제) gross-up 차액.
        // 은퇴나이가 55세 미만(퇴직연금 미개시)이면 첫 소득원이 사실상 이것뿐이라 노출이 중요하다.
        private long monthlyStockTax;
        private long totalMonthlyTax;
        private boolean isPreciseHealthInsurance;
        private long healthInsuranceIncomePart;
        private long healthInsurancePropertyPart;
        private long propertyDeductionApplied;
    }

    // M15: 건강보험 피부양자 자격 상실 가능성 추정(D-168). 확정 판정이 아니라
    // 공적연금+사적연금(둘 다 세전, 100% 반영 — 국민건강보험공단 확인 기준)만 더한
    // 단순 추정치다. 금융소득(이자·배당)·근로·사업소득 등 다른 소득원은 포함하지
    // 않으므로 실제로는 이보다 더 일찍 탈락 기준을 넘을 수도 있다 — message에 항상 명시.
    @Getter @Builder
    public static class DependentStatusWarning {
        private boolean atRisk;
        private long estimatedAnnualIncome;
        private long thresholdAnnualIncome;
        private String message;
    }

    // M16: 은퇴 후 LIQUID(주식/ETF) 수익률만 확률분포(평균 3%, 표준편차 8%,
    // 은퇴 후 보수적 자산배분 가정)로 대체해 1,000회 반복한 결과. 확정 예측이
    // 아니라 "이 가정 하에서 몇 %가 90세까지 버텼는지" 참고용 지표.
    @Getter @Builder
    public static class MonteCarloResult {
        private int successRatePercent;
        private long p10EndingBalance;
        private long p50EndingBalance;
        private long p90EndingBalance;
        private int runs;
        private double assumedReturnStddev;
    }

    @Getter @Builder
    public static class TaxBenefit {
        private double taxCreditRate;
        private String incomeLevel;
        private long annualIncome;
        private long currentAnnualContribution;
        private long irpCurrentAnnual;
        private long irpAnnualLimit;
        private long irpRemainingLimit;
        private long pensionSavingsCurrentAnnual;
        private long pensionSavingsAnnualLimit;
        private long pensionSavingsRemainingLimit;
        private long currentTaxCredit;
        private long maxTaxCredit;
        private long additionalPossibleCredit;
        private long recommendedMonthlyIrp;
        private long recommendedMonthlyPensionSavings;
        private String optimizationTip;
    }

    @Getter @Builder
    public static class Meta {
        private int yearsUntilRetirement;
        private int totalPensionYears;
        private double inflationRate;
        private double salaryGrowthRate;
        private double postRetirementReturnRate;
        private int lifeExpectancy;
        private int nationalPensionReceiptAge;
        private String pensionType;
        private int militaryServiceMonths;
        private int childrenCount;
    }

    /**
     * 특정 나이의 세후 월 소득 구성 한 점.
     * national/mid/liquid는 결과 화면 차트의 3개 시리즈와 1:1 대응한다.
     */
    @Getter @Builder
    public static class YearlyIncomePoint {
        private int age;
        private long nationalAfterTax;
        // 퇴직연금(DB/DC) — 근속연수·급여만으로 자동 계산되며 IRP·연금저축과 무관
        private long retirementPensionAfterTax;
        // IRP + 연금저축 합산 — 사용자가 직접 납입액을 입력해야 반영됨
        private long privatePensionAfterTax;
        private long liquidWithdrawalAfterTax;
        private long targetExpense; // 그 나이 시점 인플레 반영 목표 생활비 (참고선)
    }
}