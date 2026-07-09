package com.nowgnodeel.retirement_planner.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SimulationResponseDto {

    private Summary summary;
    private Breakdown breakdown;
    private TaxDetail taxDetail;
    private TaxBenefit taxBenefit;
    private Meta meta;

    // 은퇴 시점~90세까지 연도별 소득 구성. 결과 화면 차트용.
    private List<YearlyIncomePoint> incomeTimeline;

    @Getter @Builder
    public static class Summary {
        private long totalMonthlyIncome;
        private long totalMonthlyIncomeGross;
        private long targetMonthlyExpense;
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

    @Getter @Builder
    public static class TaxDetail {
        private double pensionIncomeTaxRate;
        private double healthInsuranceRate;
        private long monthlyPensionTax;
        private long monthlyHealthInsurance;
        private long totalMonthlyTax;
        private boolean isPreciseHealthInsurance;
        private long healthInsuranceIncomePart;
        private long healthInsurancePropertyPart;
        private long propertyDeductionApplied;
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
        private long midAfterTax;
        private long liquidWithdrawalAfterTax;
        private long targetExpense; // 그 나이 시점 인플레 반영 목표 생활비 (참고선)
    }
}