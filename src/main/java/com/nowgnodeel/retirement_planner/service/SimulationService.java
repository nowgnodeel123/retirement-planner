package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final double INFLATION_RATE = 0.025;
    private static final double SALARY_GROWTH_RATE = 0.05;
    private static final int LIFE_EXPECTANCY = 90;

    private static final double INCOME_REPLACEMENT_COEFFICIENT = 0.1075;
    private static final double NATIONAL_PENSION_INCOME_CAP = 637.0;
    private static final int NATIONAL_PENSION_NORMAL_AGE = 65;
    private static final int NATIONAL_PENSION_EARLY_MIN_AGE = 60;
    private static final int NATIONAL_PENSION_LATE_MAX_AGE = 70;
    private static final int MAX_MILITARY_CREDIT_MONTHS = 12;

    private static final double PRIVATE_PENSION_SEPARATE_TAX_LIMIT = 1500.0;
    private static final double PRIVATE_PENSION_SEPARATE_TAX_RATE = 0.15;

    private static final double RETIREMENT_TAX_REDUCTION_RATE_EARLY = 0.70;
    private static final double RETIREMENT_TAX_REDUCTION_RATE_LATE = 0.60;

    private static final double PENSION_SAVINGS_TAX_LIMIT = 600.0;
    private static final double IRP_TAX_LIMIT = 300.0;
    private static final double IRP_PENSION_COMBINED_LIMIT = 900.0;
    private static final double TAX_CREDIT_RATE_LOW = 0.165;
    private static final double TAX_CREDIT_RATE_HIGH = 0.132;
    private static final double INCOME_THRESHOLD = 5500.0;

    private static final double HEALTH_INSURANCE_RATE = 0.0709;
    private static final double NATIONAL_PENSION_INCOME_RATIO = 0.5;

    private static final double STOCK_TAX_RATE = 0.22;
    private static final double STOCK_TAX_EXEMPT = 250.0;
    private static final double DEPOSIT_TAX_RATE = 0.154;

    @Value("${app.share-url}")
    private String shareUrl;

    @Value("${app.national-pension.a-value}")
    private double aValue;

    public SimulationResponseDto calculate(SimulationRequestDto req) {
        int yearsUntilRetirement = req.getRetirementAge() - req.getCurrentAge();
        int payoutYears = LIFE_EXPECTANCY - req.getRetirementAge();

        int childCredit = calculateChildCredit(req.getChildrenCount());
        int militaryCredit = Math.min(req.getMilitaryServiceMonths(), MAX_MILITARY_CREDIT_MONTHS) / 12;
        int totalPensionYears = req.getPensionYearsPaid() + yearsUntilRetirement + childCredit + militaryCredit;
        int pensionReceiptAge = determinePensionReceiptAge(req);

        long nationalPensionGross = calculateNationalPension(req, totalPensionYears, pensionReceiptAge);

        long retirementPensionGross = calculateRetirementPension(req, yearsUntilRetirement, payoutYears);
        long retirementPensionAfterTax = applyRetirementIncomeTax(retirementPensionGross, payoutYears);

        long irpGross = calculateIrpOrPensionSavings(yearsUntilRetirement, req.getMonthlyIrpContribution() * 12, req.getCurrentIrpBalance(), req.getIrpReturnRate(), payoutYears);
        long pensionSavingsGross = calculateIrpOrPensionSavings(yearsUntilRetirement, req.getMonthlyPensionSavingsContribution() * 12, req.getCurrentPensionSavingsBalance(), req.getPensionSavingsReturnRate(), payoutYears);

        long privatePensionPoolGross = irpGross + pensionSavingsGross;
        double privatePensionTaxRate = calculatePrivatePensionTaxRate(req.getRetirementAge(), privatePensionPoolGross);
        long irpAfterTax = Math.round(irpGross * (1 - privatePensionTaxRate));
        long pensionSavingsAfterTax = Math.round(pensionSavingsGross * (1 - privatePensionTaxRate));

        long stockMonthly = calculateStockAsset(req, yearsUntilRetirement, payoutYears);
        long depositMonthly = calculateDepositAsset(req, yearsUntilRetirement, payoutYears);

        long healthInsurance = Math.round(nationalPensionGross * NATIONAL_PENSION_INCOME_RATIO * HEALTH_INSURANCE_RATE);
        long nationalPensionAfterTax = Math.max(0, nationalPensionGross - healthInsurance);

        long totalGross = nationalPensionGross + retirementPensionGross + irpGross + pensionSavingsGross + stockMonthly + depositMonthly;
        long totalAfterTax = nationalPensionAfterTax + retirementPensionAfterTax + irpAfterTax + pensionSavingsAfterTax + stockMonthly + depositMonthly;

        long target = Math.round(req.getTargetMonthlyExpense());
        long shortfall = totalAfterTax - target;

        int estimatedRetirementAge = calculateEstimatedRetirementAge(req);
        int estimatedYearsUntilRetirement = estimatedRetirementAge - req.getCurrentAge();
        String message = generateMessage(shortfall, req, estimatedRetirementAge);
        String shareMessage = estimatedRetirementAge + "세에 은퇴할 수 있대. 너는? → " + shareUrl;

        long privatePensionTaxAmount = Math.round(privatePensionPoolGross * privatePensionTaxRate);

        return SimulationResponseDto.builder()
                .summary(SimulationResponseDto.Summary.builder()
                        .totalMonthlyIncome(totalAfterTax)
                        .totalMonthlyIncomeGross(totalGross)
                        .targetMonthlyExpense(target)
                        .monthlyShortfall(shortfall)
                        .estimatedRetirementAge(estimatedRetirementAge)
                        .message(message)
                        .shareMessage(shareMessage)
                        .build())
                .breakdown(SimulationResponseDto.Breakdown.builder()
                        .nationalPension(nationalPensionAfterTax)
                        .retirementPension(retirementPensionAfterTax)
                        .retirementPensionGross(retirementPensionGross)
                        .irp(irpAfterTax)
                        .irpGross(irpGross)
                        .pensionSavings(pensionSavingsAfterTax)
                        .pensionSavingsGross(pensionSavingsGross)
                        .pensionSavingsTaxBenefit(calculatePensionSavingsTaxBenefit(req.getMonthlyPensionSavingsContribution()))
                        .stockAsset(stockMonthly)
                        .depositAsset(depositMonthly)
                        .build())
                .taxDetail(SimulationResponseDto.TaxDetail.builder()
                        .pensionIncomeTaxRate(privatePensionTaxRate * 100)
                        .healthInsuranceRate(HEALTH_INSURANCE_RATE * 100)
                        .monthlyPensionTax(privatePensionTaxAmount)
                        .monthlyHealthInsurance(healthInsurance)
                        .totalMonthlyTax(privatePensionTaxAmount + healthInsurance)
                        .build())
                .taxBenefit(calculateTaxBenefit(req))
                .meta(SimulationResponseDto.Meta.builder()
                        .yearsUntilRetirement(estimatedYearsUntilRetirement)
                        .totalPensionYears(totalPensionYears)
                        .inflationRate(INFLATION_RATE)
                        .salaryGrowthRate(SALARY_GROWTH_RATE)
                        .lifeExpectancy(LIFE_EXPECTANCY)
                        .nationalPensionReceiptAge(pensionReceiptAge)
                        .pensionType(req.getPensionType())
                        .militaryServiceMonths(req.getMilitaryServiceMonths())
                        .childrenCount(req.getChildrenCount())
                        .build())
                .build();
    }

    private int determinePensionReceiptAge(SimulationRequestDto req) {
        if ("EARLY".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.max(NATIONAL_PENSION_EARLY_MIN_AGE, Math.min(NATIONAL_PENSION_NORMAL_AGE - 1, req.getNationalPensionReceiptAge()));
        }
        if ("LATE".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.max(NATIONAL_PENSION_NORMAL_AGE + 1, Math.min(NATIONAL_PENSION_LATE_MAX_AGE, req.getNationalPensionReceiptAge()));
        }
        return NATIONAL_PENSION_NORMAL_AGE;
    }

    private int calculateChildCredit(int childrenCount) {
        if (childrenCount <= 0) return 0;
        if (childrenCount == 1) return 1;
        if (childrenCount == 2) return 2;
        return 2 + (childrenCount - 2);
    }

    private long calculateNationalPension(SimulationRequestDto req, int totalPensionYears, int receiptAge) {
        if (totalPensionYears < 10) return 0;
        double avgB = calculateAverageSalaryB(req.getMonthlyIncome(), req.getRetirementAge() - req.getCurrentAge(), req.getPensionYearsPaid());
        double basicPension = INCOME_REPLACEMENT_COEFFICIENT * (aValue + avgB) * (1 + 0.05 * (totalPensionYears - 20));

        if (receiptAge < NATIONAL_PENSION_NORMAL_AGE) {
            basicPension *= (1 - (NATIONAL_PENSION_NORMAL_AGE - receiptAge) * 0.06);
        } else if (receiptAge > NATIONAL_PENSION_NORMAL_AGE) {
            basicPension *= (1 + (receiptAge - NATIONAL_PENSION_NORMAL_AGE) * 0.072);
        }
        return Math.max(0, Math.round(basicPension));
    }

    private double calculateAverageSalaryB(double currentSalary, int yearsUntilRetirement, int pensionYearsPaid) {
        double totalIncome = Math.min(currentSalary, NATIONAL_PENSION_INCOME_CAP);
        for (int i = pensionYearsPaid; i >= 1; i--) {
            totalIncome += Math.min(currentSalary / Math.pow(1 + SALARY_GROWTH_RATE, i), NATIONAL_PENSION_INCOME_CAP);
        }
        for (int i = 1; i <= yearsUntilRetirement; i++) {
            totalIncome += Math.min(currentSalary * Math.pow(1 + SALARY_GROWTH_RATE, i), NATIONAL_PENSION_INCOME_CAP);
        }
        return totalIncome / (pensionYearsPaid + yearsUntilRetirement + 1);
    }

    private long calculateRetirementPension(SimulationRequestDto req, int years, int payoutYears) {
        if (years <= 0) return 0;
        if ("DB".equals(req.getPensionType())) {
            double finalSalary = req.getMonthlyIncome() * Math.pow(1 + SALARY_GROWTH_RATE, years);
            double retirementPay = finalSalary * years;
            return calculateAnnuityPayment(retirementPay, realRate(0.03), payoutYears * 12);
        }
        double realReturn = realRate(req.getPensionReturnRate());
        double realGrowth = realRate(SALARY_GROWTH_RATE);
        double fv;
        if (Math.abs(realReturn - realGrowth) < 0.0001) {
            fv = req.getMonthlyIncome() * years * Math.pow(1 + realReturn, years);
        } else {
            fv = req.getMonthlyIncome() * (Math.pow(1 + realReturn, years) - Math.pow(1 + realGrowth, years)) / (realReturn - realGrowth);
        }
        return calculateAnnuityPayment(fv, realRate(req.getPensionReturnRate()), payoutYears * 12);
    }

    private long applyRetirementIncomeTax(long monthlyRetirementPension, int payoutYears) {
        if (monthlyRetirementPension <= 0) return 0;
        double reductionRate = payoutYears > 10 ? RETIREMENT_TAX_REDUCTION_RATE_LATE : RETIREMENT_TAX_REDUCTION_RATE_EARLY;
        double assumedEffectiveRate = 0.06;
        double finalRate = assumedEffectiveRate * reductionRate;
        return Math.round(monthlyRetirementPension * (1 - finalRate));
    }

    private long calculateIrpOrPensionSavings(int years, double annualContribution, double currentBalance, double returnRate, int payoutYears) {
        double realReturn = realRate(returnRate);
        double existingFv = currentBalance > 0 ? currentBalance * Math.pow(1 + realReturn, years) : 0;
        double newFv = 0;
        if (annualContribution > 0 && years > 0) {
            newFv = realReturn != 0
                    ? annualContribution * (Math.pow(1 + realReturn, years) - 1) / realReturn
                    : annualContribution * years;
        }
        return calculateAnnuityPayment(existingFv + newFv, realReturn, payoutYears * 12);
    }

    private double calculatePrivatePensionTaxRate(int retirementAge, long monthlyPrivatePensionGross) {
        long annualIncome = monthlyPrivatePensionGross * 12;

        if (annualIncome <= PRIVATE_PENSION_SEPARATE_TAX_LIMIT) {
            if (retirementAge < 70) return 0.055;
            if (retirementAge < 80) return 0.044;
            return 0.033;
        }

        double deduction = calculatePensionIncomeDeduction(annualIncome);
        double taxableIncome = Math.max(0, annualIncome - deduction);
        double comprehensiveTax = calculateComprehensiveIncomeTax(taxableIncome);
        double comprehensiveRate = annualIncome > 0 ? comprehensiveTax / annualIncome : PRIVATE_PENSION_SEPARATE_TAX_RATE;

        return Math.min(comprehensiveRate, PRIVATE_PENSION_SEPARATE_TAX_RATE);
    }

    private double calculatePensionIncomeDeduction(long annualIncome) {
        if (annualIncome <= 350) return annualIncome;
        if (annualIncome <= 700) return 350 + (annualIncome - 350) * 0.4;
        if (annualIncome <= 1400) return 490 + (annualIncome - 700) * 0.2;
        if (annualIncome <= 2000) return 630 + (annualIncome - 1400) * 0.1;
        return Math.min(690 + (annualIncome - 2000) * 0.05, 900);
    }

    private double calculateComprehensiveIncomeTax(double taxableIncome) {
        if (taxableIncome <= 1400) return taxableIncome * 0.06;
        if (taxableIncome <= 5000) return 84 + (taxableIncome - 1400) * 0.15;
        if (taxableIncome <= 8800) return 624 + (taxableIncome - 5000) * 0.24;
        if (taxableIncome <= 15000) return 1536 + (taxableIncome - 8800) * 0.35;
        if (taxableIncome <= 30000) return 3706 + (taxableIncome - 15000) * 0.38;
        if (taxableIncome <= 50000) return 9406 + (taxableIncome - 30000) * 0.40;
        return 17406 + (taxableIncome - 50000) * 0.42;
    }

    private long calculateStockAsset(SimulationRequestDto req, int years, int payoutYears) {
        if (req.getStockAssetBalance() == 0 && req.getMonthlyStockInvestment() == 0) return 0;
        double realReturn = realRate(req.getStockReturnRate());
        double existingFv = req.getStockAssetBalance() * Math.pow(1 + realReturn, years);
        double newFv = req.getMonthlyStockInvestment() > 0 && realReturn != 0 && years > 0
                ? req.getMonthlyStockInvestment() * 12 * (Math.pow(1 + realReturn, years) - 1) / realReturn : 0;
        double totalFv = existingFv + newFv;
        double totalCost = req.getStockAssetBalance() + req.getMonthlyStockInvestment() * 12 * years;
        double totalGain = Math.max(0, totalFv - totalCost);
        double annualGain = years > 0 ? totalGain / years : 0;
        double annualTax = Math.max(0, annualGain - STOCK_TAX_EXEMPT) * STOCK_TAX_RATE;
        double fvAfterTax = Math.max(0, totalFv - annualTax * years);
        return calculateAnnuityPayment(fvAfterTax, realReturn, payoutYears * 12);
    }

    private long calculateDepositAsset(SimulationRequestDto req, int years, int payoutYears) {
        if (req.getDepositBalance() == 0 && req.getMonthlyDepositInvestment() == 0) return 0;
        double realReturn = realRate(req.getDepositReturnRate());
        double existingFv = req.getDepositBalance() * Math.pow(1 + realReturn, years);
        double newFv = req.getMonthlyDepositInvestment() > 0 && realReturn != 0 && years > 0
                ? req.getMonthlyDepositInvestment() * 12 * (Math.pow(1 + realReturn, years) - 1) / realReturn : 0;
        double totalFv = existingFv + newFv;
        double totalCost = req.getDepositBalance() + req.getMonthlyDepositInvestment() * 12 * years;
        double interest = Math.max(0, totalFv - totalCost);
        double fvAfterTax = totalFv - interest * DEPOSIT_TAX_RATE;
        return payoutYears > 0 ? Math.round(Math.max(0, fvAfterTax) / (payoutYears * 12)) : 0;
    }

    private long calculateAnnuityPayment(double fv, double annualRate, int months) {
        if (fv <= 0 || months <= 0) return 0;
        double monthlyRate = annualRate / 12;
        if (monthlyRate <= 0) return Math.round(fv / months);
        return Math.round(fv * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months)));
    }

    private long calculatePensionSavingsTaxBenefit(double monthlyContribution) {
        return Math.round(Math.min(monthlyContribution * 12, PENSION_SAVINGS_TAX_LIMIT) * TAX_CREDIT_RATE_LOW);
    }

    private int calculateEstimatedRetirementAge(SimulationRequestDto req) {
        for (int age = req.getCurrentAge() + 1; age <= 75; age++) {
            long afterTax = calculateTotalAfterTaxForAge(req, age, req.getMonthlyIrpContribution(), req.getMonthlyPensionSavingsContribution());
            if (afterTax >= req.getTargetMonthlyExpense()) return age;
        }
        return 75;
    }

    private long calculateTotalAfterTaxForAge(SimulationRequestDto req, int age, double irpMonthly, double psMonthly) {
        int years = age - req.getCurrentAge();
        int payoutYears = LIFE_EXPECTANCY - age;
        if (payoutYears <= 0) return 0;

        int childCredit = calculateChildCredit(req.getChildrenCount());
        int militaryCredit = Math.min(req.getMilitaryServiceMonths(), MAX_MILITARY_CREDIT_MONTHS) / 12;
        int totalPensionYears = req.getPensionYearsPaid() + years + childCredit + militaryCredit;
        int pensionReceiptAge = determinePensionReceiptAge(req);

        long np = calculateNationalPension(req, totalPensionYears, pensionReceiptAge);
        long healthInsurance = Math.round(np * NATIONAL_PENSION_INCOME_RATIO * HEALTH_INSURANCE_RATE);
        long npAfterTax = Math.max(0, np - healthInsurance);

        long rp = calculateRetirementPension(req, years, payoutYears);
        long rpAfterTax = applyRetirementIncomeTax(rp, payoutYears);

        long irp = calculateIrpOrPensionSavings(years, irpMonthly * 12, req.getCurrentIrpBalance(), req.getIrpReturnRate(), payoutYears);
        long ps = calculateIrpOrPensionSavings(years, psMonthly * 12, req.getCurrentPensionSavingsBalance(), req.getPensionSavingsReturnRate(), payoutYears);
        long privatePool = irp + ps;
        double privateRate = calculatePrivatePensionTaxRate(age, privatePool);
        long privateAfterTax = Math.round(privatePool * (1 - privateRate));

        long stock = calculateStockAsset(req, years, payoutYears);
        long deposit = calculateDepositAsset(req, years, payoutYears);

        return npAfterTax + rpAfterTax + privateAfterTax + stock + deposit;
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

        double totalEligible = Math.min(Math.min(psCurrentAnnual, PENSION_SAVINGS_TAX_LIMIT) + irpCurrentAnnual, IRP_PENSION_COMBINED_LIMIT);
        long currentTaxCredit = Math.round(totalEligible * taxCreditRate);
        long maxTaxCredit = Math.round(IRP_PENSION_COMBINED_LIMIT * taxCreditRate);
        long additionalPossibleCredit = Math.max(0, maxTaxCredit - currentTaxCredit);

        String tip = generateOptimizationTip(irpRemaining, psRemaining, additionalPossibleCredit);

        return SimulationResponseDto.TaxBenefit.builder()
                .taxCreditRate(taxCreditRate * 100).incomeLevel(incomeLevel)
                .annualIncome(Math.round(annualIncome))
                .currentAnnualContribution(irpCurrentAnnual + psCurrentAnnual)
                .irpCurrentAnnual(irpCurrentAnnual).irpAnnualLimit((long) IRP_TAX_LIMIT).irpRemainingLimit(irpRemaining)
                .pensionSavingsCurrentAnnual(psCurrentAnnual).pensionSavingsAnnualLimit((long) PENSION_SAVINGS_TAX_LIMIT).pensionSavingsRemainingLimit(psRemaining)
                .currentTaxCredit(currentTaxCredit).maxTaxCredit(maxTaxCredit).additionalPossibleCredit(additionalPossibleCredit)
                .recommendedMonthlyIrp(25).recommendedMonthlyPensionSavings(50).optimizationTip(tip)
                .build();
    }

    private String generateOptimizationTip(long irpRemaining, long psRemaining, long additionalCredit) {
        if (additionalCredit == 0) return "세액공제를 최대로 활용하고 있습니다! 🎉";
        StringBuilder tip = new StringBuilder();
        if (psRemaining > 0 && irpRemaining > 0) {
            tip.append("연금저축 월 ").append(Math.round(psRemaining / 12.0)).append("만원 + IRP 월 ").append(Math.round(irpRemaining / 12.0)).append("만원 더 납입하면 ");
        } else if (psRemaining > 0) {
            tip.append("연금저축을 월 ").append(Math.round(psRemaining / 12.0)).append("만원 더 납입하면 ");
        } else if (irpRemaining > 0) {
            tip.append("IRP를 월 ").append(Math.round(irpRemaining / 12.0)).append("만원 더 납입하면 ");
        }
        tip.append("연 ").append(additionalCredit).append("만원을 추가로 돌려받을 수 있습니다.");
        return tip.toString();
    }

    private String generateMessage(long shortfall, SimulationRequestDto req, int estimatedRetirementAge) {
        if (shortfall >= 0) return "목표 달성! 지금 페이스라면 " + estimatedRetirementAge + "세에 은퇴할 수 있습니다. 🎉";
        long neededIrp = req.getMonthlyIrpContribution().longValue() + Math.abs(shortfall);
        return "IRP 납입액을 월 " + req.getMonthlyIrpContribution().longValue() + "만원 → " + neededIrp + "만원으로 늘리면 목표를 달성할 수 있습니다.";
    }

    private double realRate(double nominalRate) {
        return (1 + nominalRate) / (1 + INFLATION_RATE) - 1;
    }
}