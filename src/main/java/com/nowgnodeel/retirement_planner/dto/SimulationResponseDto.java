package com.nowgnodeel.retirement_planner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimulationResponseDto {

    private Summary summary;
    private Breakdown breakdown;
    private TaxDetail taxDetail;
    private TaxBenefit taxBenefit;
    private Meta meta;

    @Getter @Builder
    public static class Summary {
        private long totalMonthlyIncome;
        private long totalMonthlyIncomeGross;
        private long targetMonthlyExpense;
        private long monthlyShortfall;
        private int estimatedRetirementAge;
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
        private long depositAsset;
    }

    @Getter @Builder
    public static class TaxDetail {
        private double pensionIncomeTaxRate;
        private double healthInsuranceRate;
        private long monthlyPensionTax;
        private long monthlyHealthInsurance;
        private long totalMonthlyTax;
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
        private int lifeExpectancy;
        private int nationalPensionReceiptAge;
        private String pensionType;
        private int militaryServiceMonths;
        private int childrenCount;
    }
}