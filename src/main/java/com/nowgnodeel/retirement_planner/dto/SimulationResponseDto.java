package com.nowgnodeel.retirement_planner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimulationResponseDto {

    private Summary summary;
    private Breakdown breakdown;
    private TaxBenefit taxBenefit;
    private Meta meta;

    @Getter
    @Builder
    public static class Summary {
        private long totalMonthlyIncome;
        private long targetMonthlyExpense;
        private long monthlyShortfall;
        private int estimatedRetirementAge;
        private String message;
        private String shareMessage;
    }

    @Getter
    @Builder
    public static class Breakdown {
        private long nationalPension;
        private long retirementPension;
        private long irp;
        private long pensionSavings;
        private long pensionSavingsTaxBenefit;
    }

    @Getter
    @Builder
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

    @Getter
    @Builder
    public static class Meta {
        private int yearsUntilRetirement;
        private int totalPensionYears;
        private double inflationRate;
    }
}