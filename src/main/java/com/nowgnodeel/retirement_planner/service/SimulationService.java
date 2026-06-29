package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final int PAYOUT_YEARS = 25;
    private static final double INFLATION_RATE = 0.025;
    private static final double PENSION_SAVINGS_TAX_LIMIT = 600.0;
    private static final double IRP_TAX_LIMIT = 300.0;
    private static final double IRP_PENSION_COMBINED_LIMIT = 900.0;
    private static final double TAX_CREDIT_RATE_LOW = 0.165;
    private static final double TAX_CREDIT_RATE_HIGH = 0.132;
    private static final double INCOME_THRESHOLD = 5500.0;

    @Value("${app.share-url}")
    private String shareUrl;

    @Value("${app.national-pension.a-value}")
    private double aValue;

    public SimulationResponseDto calculate(SimulationRequestDto req) {
        int yearsUntilRetirement = req.getRetirementAge() - req.getCurrentAge();
        int totalPensionYears = req.getPensionYearsPaid() + yearsUntilRetirement;

        long nationalPension = calculateNationalPension(totalPensionYears, req.getMonthlyIncome());
        long retirementPension = calculateFv(yearsUntilRetirement, req.getMonthlyIncome(), realRate(req.getPensionReturnRate()));
        long irp = calculateFv(yearsUntilRetirement, req.getMonthlyIrpContribution() * 12, realRate(req.getIrpReturnRate()));
        long pensionSavings = calculateFv(yearsUntilRetirement, req.getMonthlyPensionSavingsContribution() * 12, realRate(req.getPensionSavingsReturnRate()));
        long pensionSavingsTaxBenefit = calculatePensionSavingsTaxBenefit(req.getMonthlyPensionSavingsContribution());

        long totalMonthlyIncome = nationalPension + retirementPension + irp + pensionSavings;
        long target = Math.round(req.getTargetMonthlyExpense());
        long shortfall = totalMonthlyIncome - target;

        int estimatedRetirementAge = calculateEstimatedRetirementAge(req);
        int estimatedYearsUntilRetirement = estimatedRetirementAge - req.getCurrentAge();

        String message = generateMessage(shortfall, req, estimatedRetirementAge);
        String shareMessage = estimatedRetirementAge + "세에 은퇴할 수 있대. 너는? → " + shareUrl;

        return SimulationResponseDto.builder()
                .summary(SimulationResponseDto.Summary.builder()
                        .totalMonthlyIncome(totalMonthlyIncome)
                        .targetMonthlyExpense(target)
                        .monthlyShortfall(shortfall)
                        .estimatedRetirementAge(estimatedRetirementAge)
                        .message(message)
                        .shareMessage(shareMessage)
                        .build())
                .breakdown(SimulationResponseDto.Breakdown.builder()
                        .nationalPension(nationalPension)
                        .retirementPension(retirementPension)
                        .irp(irp)
                        .pensionSavings(pensionSavings)
                        .pensionSavingsTaxBenefit(pensionSavingsTaxBenefit)
                        .build())
                .taxBenefit(calculateTaxBenefit(req))
                .meta(SimulationResponseDto.Meta.builder()
                        .yearsUntilRetirement(estimatedYearsUntilRetirement)
                        .totalPensionYears(totalPensionYears)
                        .inflationRate(INFLATION_RATE)
                        .build())
                .build();
    }

    private SimulationResponseDto.TaxBenefit calculateTaxBenefit(SimulationRequestDto req) {
        double annualIncome = req.getMonthlyIncome() * 12;
        double taxCreditRate = annualIncome <= INCOME_THRESHOLD ? TAX_CREDIT_RATE_LOW : TAX_CREDIT_RATE_HIGH;

        String incomeLevel = annualIncome <= INCOME_THRESHOLD
                ? "연 소득 5,500만원 이하 (세액공제율 16.5%)"
                : "연 소득 5,500만원 초과 (세액공제율 13.2%)";

        long irpCurrentAnnual = Math.round(req.getMonthlyIrpContribution() * 12);
        long psCurrentAnnual = Math.round(req.getMonthlyPensionSavingsContribution() * 12);

        long irpRemaining = Math.max(0, (long) IRP_TAX_LIMIT - irpCurrentAnnual);
        long psRemaining = Math.max(0, (long) PENSION_SAVINGS_TAX_LIMIT - psCurrentAnnual);

        double psEligible = Math.min(psCurrentAnnual, PENSION_SAVINGS_TAX_LIMIT);
        double totalEligible = Math.min(psEligible + irpCurrentAnnual, IRP_PENSION_COMBINED_LIMIT);

        long currentTaxCredit = Math.round(totalEligible * taxCreditRate);
        long maxTaxCredit = Math.round(IRP_PENSION_COMBINED_LIMIT * taxCreditRate);
        long additionalPossibleCredit = Math.max(0, maxTaxCredit - currentTaxCredit);

        String optimizationTip = generateOptimizationTip(
                irpCurrentAnnual, psCurrentAnnual,
                irpRemaining, psRemaining,
                additionalPossibleCredit
        );

        return SimulationResponseDto.TaxBenefit.builder()
                .taxCreditRate(taxCreditRate * 100)
                .incomeLevel(incomeLevel)
                .annualIncome(Math.round(annualIncome))
                .currentAnnualContribution(irpCurrentAnnual + psCurrentAnnual)
                .irpCurrentAnnual(irpCurrentAnnual)
                .irpAnnualLimit((long) IRP_TAX_LIMIT)
                .irpRemainingLimit(irpRemaining)
                .pensionSavingsCurrentAnnual(psCurrentAnnual)
                .pensionSavingsAnnualLimit((long) PENSION_SAVINGS_TAX_LIMIT)
                .pensionSavingsRemainingLimit(psRemaining)
                .currentTaxCredit(currentTaxCredit)
                .maxTaxCredit(maxTaxCredit)
                .additionalPossibleCredit(additionalPossibleCredit)
                .recommendedMonthlyIrp(25)
                .recommendedMonthlyPensionSavings(50)
                .optimizationTip(optimizationTip)
                .build();
    }

    private String generateOptimizationTip(
            long irpAnnual, long psAnnual,
            long irpRemaining, long psRemaining,
            long additionalCredit) {

        if (additionalCredit == 0) {
            return "세액공제를 최대로 활용하고 있습니다! 🎉";
        }

        StringBuilder tip = new StringBuilder();

        if (psRemaining > 0 && irpRemaining > 0) {
            tip.append("연금저축을 월 ").append(Math.round(psRemaining / 12.0)).append("만원, ");
            tip.append("IRP를 월 ").append(Math.round(irpRemaining / 12.0)).append("만원 더 납입하면 ");
        } else if (psRemaining > 0) {
            tip.append("연금저축을 월 ").append(Math.round(psRemaining / 12.0)).append("만원 더 납입하면 ");
        } else if (irpRemaining > 0) {
            tip.append("IRP를 월 ").append(Math.round(irpRemaining / 12.0)).append("만원 더 납입하면 ");
        }

        tip.append("연 ").append(additionalCredit).append("만원을 추가로 돌려받을 수 있습니다.");
        return tip.toString();
    }

    private String generateMessage(long shortfall, SimulationRequestDto req, int estimatedRetirementAge) {
        if (shortfall >= 0) {
            return "목표 달성! 지금 페이스라면 " + estimatedRetirementAge + "세에 은퇴할 수 있습니다. 🎉";
        }
        long neededIrp = req.getMonthlyIrpContribution().longValue() + Math.abs(shortfall);
        return "IRP 납입액을 월 " + req.getMonthlyIrpContribution().longValue() + "만원 → "
                + neededIrp + "만원으로 늘리면 목표를 달성할 수 있습니다.";
    }

    private double realRate(double nominalRate) {
        return (1 + nominalRate) / (1 + INFLATION_RATE) - 1;
    }

    private long calculateFv(int years, double annualContribution, double rate) {
        if (annualContribution == 0 || rate == 0) return 0;
        double fv = annualContribution * (Math.pow(1 + rate, years) - 1) / rate;
        return Math.round(fv / (PAYOUT_YEARS * 12));
    }

    private long calculateNationalPension(int totalYears, double monthlyIncome) {
        if (totalYears < 10) return 0;
        return Math.round(0.1 * (aValue + monthlyIncome) * (1 + 0.05 * (totalYears - 20)));
    }

    private long calculatePensionSavingsTaxBenefit(double monthlyContribution) {
        double annual = monthlyContribution * 12;
        return Math.round(Math.min(annual, PENSION_SAVINGS_TAX_LIMIT) * TAX_CREDIT_RATE_LOW);
    }

    private int calculateEstimatedRetirementAge(SimulationRequestDto req) {
        for (int age = req.getCurrentAge() + 1; age <= 75; age++) {
            int years = age - req.getCurrentAge();
            int pensionYears = req.getPensionYearsPaid() + years;

            long np = calculateNationalPension(pensionYears, req.getMonthlyIncome());
            long rp = calculateFv(years, req.getMonthlyIncome(), realRate(req.getPensionReturnRate()));
            long irp = calculateFv(years, req.getMonthlyIrpContribution() * 12, realRate(req.getIrpReturnRate()));
            long ps = calculateFv(years, req.getMonthlyPensionSavingsContribution() * 12, realRate(req.getPensionSavingsReturnRate()));

            if (np + rp + irp + ps >= req.getTargetMonthlyExpense()) {
                return age;
            }
        }
        return 75;
    }
}