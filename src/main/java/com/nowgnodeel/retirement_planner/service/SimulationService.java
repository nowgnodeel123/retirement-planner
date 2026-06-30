package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final double INFLATION_RATE = 0.025;
    private static final int LIFE_EXPECTANCY = 90;
    private static final double POST_RETIREMENT_NOMINAL_RATE = 0.03;

    // ── 생애 연봉 곡선 (단순화 모델) ──
    // 20~44세: 연 5% 상승 (경력 성장기)
    // 45~49세: 연 1% 상승 (정점 근접)
    // 50~59세: 0% (정체, 임금피크제)
    // 60세 이후: 연 -2% (재취업/이직 시 하락 가정)
    private static final int GROWTH_PHASE_END_AGE = 44;
    private static final int SLOWDOWN_PHASE_END_AGE = 49;
    private static final int PLATEAU_PHASE_END_AGE = 59;
    private static final double GROWTH_RATE = 0.05;
    private static final double SLOWDOWN_RATE = 0.01;
    private static final double PLATEAU_RATE = 0.0;
    private static final double DECLINE_RATE = -0.02;

    private static final double INCOME_REPLACEMENT_COEFFICIENT = 0.1075;
    private static final double NATIONAL_PENSION_INCOME_CAP = 637.0;
    private static final int NATIONAL_PENSION_NORMAL_AGE = 65;
    private static final int NATIONAL_PENSION_EARLY_MIN_AGE = 60;
    private static final int NATIONAL_PENSION_LATE_MAX_AGE = 70;
    private static final int MAX_MILITARY_CREDIT_MONTHS = 12;

    private static final double PRIVATE_PENSION_SEPARATE_TAX_LIMIT = 1500.0;
    private static final double PRIVATE_PENSION_SEPARATE_TAX_RATE = 0.15;

    private static final double PENSION_RECEIPT_PAYMENT_RATE_WITHIN_10Y = 0.70;
    private static final double PENSION_RECEIPT_PAYMENT_RATE_AFTER_10Y = 0.60;

    private static final double PENSION_SAVINGS_TAX_LIMIT = 600.0;
    private static final double IRP_TAX_LIMIT = 300.0;
    private static final double IRP_PENSION_COMBINED_LIMIT = 900.0;
    private static final double TAX_CREDIT_RATE_LOW = 0.165;
    private static final double TAX_CREDIT_RATE_HIGH = 0.132;
    private static final double INCOME_THRESHOLD = 5500.0;

    private static final double HEALTH_INSURANCE_RATE = 0.0709;
    private static final double NATIONAL_PENSION_INCOME_RATIO = 0.5;

    private static final double PROPERTY_DEDUCTION = 10000.0;
    private static final double PROPERTY_INSURANCE_RATE = 0.000911;

    private static final double STOCK_TAX_RATE = 0.22;
    private static final double STOCK_TAX_EXEMPT = 250.0;

    private static final String MONTHLY_AMOUNT_SUFFIX = "만원";

    @Value("${app.share-url}")
    private String shareUrl;

    @Value("${app.national-pension.a-value}")
    private double aValue;

    // ── 특정 나이의 연봉을 생애 곡선 기준으로 계산 ──
    // currentAge에 currentSalary를 갖고 있다고 가정하고, targetAge 시점의 연봉을 역산/예측한다.
    private double projectSalaryAtAge(double currentSalary, int currentAge, int targetAge) {
        if (targetAge == currentAge) return currentSalary;

        if (targetAge > currentAge) {
            double salary = currentSalary;
            for (int age = currentAge; age < targetAge; age++) {
                salary *= (1 + annualGrowthRateAt(age));
            }
            return salary;
        } else {
            double salary = currentSalary;
            for (int age = currentAge - 1; age >= targetAge; age--) {
                salary /= (1 + annualGrowthRateAt(age));
            }
            return salary;
        }
    }

    // 해당 나이에서 다음 해로 넘어갈 때 적용되는 성장률
    private double annualGrowthRateAt(int age) {
        if (age <= GROWTH_PHASE_END_AGE) return GROWTH_RATE;
        if (age <= SLOWDOWN_PHASE_END_AGE) return SLOWDOWN_RATE;
        if (age <= PLATEAU_PHASE_END_AGE) return PLATEAU_RATE;
        return DECLINE_RATE;
    }

    public SimulationResponseDto calculate(SimulationRequestDto req) {

        int estimatedRetirementAge = calculateEstimatedRetirementAge(req);
        int yearsUntilRetirement = estimatedRetirementAge - req.getCurrentAge();
        int payoutYears = LIFE_EXPECTANCY - estimatedRetirementAge;

        int childCredit = calculateChildCredit(req.getChildrenCount());
        int militaryCredit = Math.clamp(req.getMilitaryServiceMonths(), 0, MAX_MILITARY_CREDIT_MONTHS) / 12;
        int totalPensionYears = req.getPensionYearsPaid() + yearsUntilRetirement + childCredit + militaryCredit;
        int pensionReceiptAge = determinePensionReceiptAge(req);

        long nationalPensionGross = calculateNationalPension(req, totalPensionYears, pensionReceiptAge, estimatedRetirementAge);

        long retirementPensionGross = calculateRetirementPension(req, yearsUntilRetirement, payoutYears, estimatedRetirementAge, false);
        long retirementPensionAfterTax = calculateRetirementPension(req, yearsUntilRetirement, payoutYears, estimatedRetirementAge, true);

        long irpGross = calculateIrpOrPensionSavings(yearsUntilRetirement, req.getMonthlyIrpContribution() * 12, req.getCurrentIrpBalance(), req.getIrpReturnRate(), payoutYears);
        long pensionSavingsGross = calculateIrpOrPensionSavings(yearsUntilRetirement, req.getMonthlyPensionSavingsContribution() * 12, req.getCurrentPensionSavingsBalance(), req.getPensionSavingsReturnRate(), payoutYears);

        long privatePensionPoolGross = irpGross + pensionSavingsGross;
        double privatePensionTaxRate = calculatePrivatePensionTaxRate(estimatedRetirementAge, privatePensionPoolGross);
        long irpAfterTax = Math.round(irpGross * (1 - privatePensionTaxRate));
        long pensionSavingsAfterTax = Math.round(pensionSavingsGross * (1 - privatePensionTaxRate));

        long stockMonthly = calculateStockAsset(req, yearsUntilRetirement, payoutYears);

        HealthInsuranceResult hiResult = calculateHealthInsurance(req, nationalPensionGross);
        long healthInsurance = hiResult.total;
        long nationalPensionAfterTax = Math.max(0, nationalPensionGross - healthInsurance);

        long totalGross = nationalPensionGross + retirementPensionGross + irpGross + pensionSavingsGross + stockMonthly;
        long totalAfterTax = nationalPensionAfterTax + retirementPensionAfterTax + irpAfterTax + pensionSavingsAfterTax + stockMonthly;

        long target = Math.round(req.getTargetMonthlyExpense());
        long shortfall = totalAfterTax - target;

        String message = generateMessage(shortfall, req, estimatedRetirementAge);
        String shareMessage = estimatedRetirementAge + "세에 은퇴할 수 있대. 너는? → " + shareUrl;

        long retirementPensionTaxAmount = retirementPensionGross - retirementPensionAfterTax;
        long privatePensionTaxAmount = Math.round(privatePensionPoolGross * privatePensionTaxRate);
        long totalPensionTax = retirementPensionTaxAmount + privatePensionTaxAmount;

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
                        .build())
                .taxDetail(SimulationResponseDto.TaxDetail.builder()
                        .pensionIncomeTaxRate(privatePensionTaxRate * 100)
                        .healthInsuranceRate(HEALTH_INSURANCE_RATE * 100)
                        .monthlyPensionTax(totalPensionTax)
                        .monthlyHealthInsurance(healthInsurance)
                        .totalMonthlyTax(totalPensionTax + healthInsurance)
                        .isPreciseHealthInsurance(hiResult.isPrecise)
                        .healthInsuranceIncomePart(hiResult.incomePart)
                        .healthInsurancePropertyPart(hiResult.propertyPart)
                        .propertyDeductionApplied(hiResult.isPrecise ? (long) PROPERTY_DEDUCTION : 0)
                        .build())
                .taxBenefit(calculateTaxBenefit(req))
                .meta(SimulationResponseDto.Meta.builder()
                        .yearsUntilRetirement(yearsUntilRetirement)
                        .totalPensionYears(totalPensionYears)
                        .inflationRate(INFLATION_RATE)
                        .salaryGrowthRate(GROWTH_RATE)
                        .postRetirementReturnRate(POST_RETIREMENT_NOMINAL_RATE)
                        .lifeExpectancy(LIFE_EXPECTANCY)
                        .nationalPensionReceiptAge(pensionReceiptAge)
                        .pensionType(req.getPensionType())
                        .militaryServiceMonths(req.getMilitaryServiceMonths())
                        .childrenCount(req.getChildrenCount())
                        .build())
                .build();
    }

    private static class HealthInsuranceResult {
        long total;
        long incomePart;
        long propertyPart;
        boolean isPrecise;
    }

    private HealthInsuranceResult calculateHealthInsurance(SimulationRequestDto req, long nationalPensionGross) {
        HealthInsuranceResult result = new HealthInsuranceResult();
        long incomePart = Math.round(nationalPensionGross * NATIONAL_PENSION_INCOME_RATIO * HEALTH_INSURANCE_RATE);

        if (!req.isUsePreciseHealthInsurance()) {
            result.total = incomePart;
            result.incomePart = incomePart;
            result.propertyPart = 0;
            result.isPrecise = false;
            return result;
        }

        double totalProperty = req.getRealEstateValue() + req.getFinancialAssetValue();
        double taxableProperty = Math.max(0, totalProperty - PROPERTY_DEDUCTION);
        long propertyPart = Math.round(taxableProperty * PROPERTY_INSURANCE_RATE);

        result.total = incomePart + propertyPart;
        result.incomePart = incomePart;
        result.propertyPart = propertyPart;
        result.isPrecise = true;
        return result;
    }

    private int determinePensionReceiptAge(SimulationRequestDto req) {
        if ("EARLY".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.clamp(req.getNationalPensionReceiptAge(), NATIONAL_PENSION_EARLY_MIN_AGE, NATIONAL_PENSION_NORMAL_AGE - 1);
        }
        if ("LATE".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.clamp(req.getNationalPensionReceiptAge(), NATIONAL_PENSION_NORMAL_AGE + 1, NATIONAL_PENSION_LATE_MAX_AGE);
        }
        return NATIONAL_PENSION_NORMAL_AGE;
    }

    private int calculateChildCredit(int childrenCount) {
        if (childrenCount <= 0) return 0;
        if (childrenCount == 1) return 1;
        if (childrenCount == 2) return 2;
        return 2 + (childrenCount - 2);
    }

    private long calculateNationalPension(SimulationRequestDto req, int totalPensionYears, int receiptAge, int retirementAge) {
        if (totalPensionYears < 10) return 0;
        double avgB = calculateAverageSalaryB(req.getMonthlyIncome(), req.getCurrentAge(), retirementAge, req.getPensionYearsPaid());
        double basicPension = INCOME_REPLACEMENT_COEFFICIENT * (aValue + avgB) * (1 + 0.05 * (totalPensionYears - 20));

        if (receiptAge < NATIONAL_PENSION_NORMAL_AGE) {
            basicPension *= (1 - (NATIONAL_PENSION_NORMAL_AGE - receiptAge) * 0.06);
        } else if (receiptAge > NATIONAL_PENSION_NORMAL_AGE) {
            basicPension *= (1 + (receiptAge - NATIONAL_PENSION_NORMAL_AGE) * 0.072);
        }
        return Math.max(0, Math.round(basicPension));
    }

    // ── 경력 평균 소득(B값): 생애 연봉 곡선 기준으로 입사~은퇴까지 매년 소득 추정 후 평균 ──
    private double calculateAverageSalaryB(double currentSalary, int currentAge, int retirementAge, int pensionYearsPaid) {
        int startAge = currentAge - pensionYearsPaid;
        double totalIncome = 0;
        int count = 0;
        for (int age = startAge; age < retirementAge; age++) {
            double salaryAtAge = projectSalaryAtAge(currentSalary, currentAge, age);
            totalIncome += Math.min(salaryAtAge, NATIONAL_PENSION_INCOME_CAP);
            count++;
        }
        return count > 0 ? totalIncome / count : currentSalary;
    }

    // ── 퇴직연금: 적립은 생애 연봉 곡선 + 입력 수익률, 인출은 보수적 고정 수익률 ──
    private long calculateRetirementPension(SimulationRequestDto req, int years, int payoutYears, int retirementAge, boolean afterTax) {
        if (years <= 0) return 0;
        double lumpSum = calculateRetirementLumpSum(req, years, retirementAge);
        double payoutRate = realRate(POST_RETIREMENT_NOMINAL_RATE);
        double payoutBase = afterTax ? applyRetirementIncomeTax(lumpSum, years, payoutYears) : lumpSum;
        return calculateAnnuityPayment(payoutBase, payoutRate, payoutYears * 12);
    }

    private double calculateRetirementLumpSum(SimulationRequestDto req, int years, int retirementAge) {
        if ("DB".equals(req.getPensionType())) {
            double finalSalary = projectSalaryAtAge(req.getMonthlyIncome(), req.getCurrentAge(), retirementAge);
            return finalSalary * years;
        }

        // DC형: 매년 그 해 연봉의 1개월치를 적립, 적립 후 입력 수익률로 복리 운용
        double realReturn = realRate(req.getPensionReturnRate());
        double fv = 0;
        for (int i = 0; i < years; i++) {
            int contributionAge = req.getCurrentAge() + i;
            double annualSalaryAtContribution = projectSalaryAtAge(req.getMonthlyIncome(), req.getCurrentAge(), contributionAge);
            int remainingYears = years - i;
            fv += annualSalaryAtContribution * Math.pow(1 + realReturn, remainingYears);
        }
        return fv;
    }

    private double applyRetirementIncomeTax(double lumpSum, double serviceYears, int payoutYears) {
        if (lumpSum <= 0 || serviceYears <= 0) return Math.max(0, lumpSum);

        double serviceDeduction = calculateServiceYearDeduction(serviceYears);
        double taxBase = Math.max(0, lumpSum - serviceDeduction);
        double convertedIncome = (taxBase / serviceYears) * 12;
        double convertedDeduction = calculateConvertedIncomeDeduction(convertedIncome);
        double taxableConvertedIncome = Math.max(0, convertedIncome - convertedDeduction);
        double convertedTax = calculateComprehensiveIncomeTax(taxableConvertedIncome);
        double calculatedTax = convertedTax * serviceYears / 12;

        double blendedPaymentRate = calculateBlendedPaymentRate(payoutYears);
        double finalTax = calculatedTax * blendedPaymentRate;

        return Math.max(0, lumpSum - finalTax);
    }

    private double calculateServiceYearDeduction(double serviceYears) {
        if (serviceYears <= 5) return 100 * serviceYears;
        if (serviceYears <= 10) return 500 + 200 * (serviceYears - 5);
        if (serviceYears <= 20) return 1500 + 250 * (serviceYears - 10);
        return 4000 + 300 * (serviceYears - 20);
    }

    private double calculateConvertedIncomeDeduction(double convertedIncome) {
        if (convertedIncome <= 800) return convertedIncome;
        if (convertedIncome <= 7000) return 800 + (convertedIncome - 800) * 0.6;
        if (convertedIncome <= 10000) return 4520 + (convertedIncome - 7000) * 0.55;
        if (convertedIncome <= 30000) return 6170 + (convertedIncome - 10000) * 0.45;
        return 15170 + (convertedIncome - 30000) * 0.35;
    }

    private double calculateBlendedPaymentRate(int payoutYears) {
        if (payoutYears <= 0) return PENSION_RECEIPT_PAYMENT_RATE_WITHIN_10Y;
        if (payoutYears <= 10) return PENSION_RECEIPT_PAYMENT_RATE_WITHIN_10Y;
        double within10 = 10 * PENSION_RECEIPT_PAYMENT_RATE_WITHIN_10Y;
        double after10 = (payoutYears - 10) * PENSION_RECEIPT_PAYMENT_RATE_AFTER_10Y;
        return (within10 + after10) / payoutYears;
    }

    private long calculateIrpOrPensionSavings(int years, double annualContribution, double currentBalance, double returnRate, int payoutYears) {
        double accumulationRate = realRate(returnRate);
        double existingFv = currentBalance > 0 ? currentBalance * Math.pow(1 + accumulationRate, years) : 0;
        double newFv = 0;
        if (annualContribution > 0 && years > 0) {
            newFv = accumulationRate != 0
                    ? annualContribution * (Math.pow(1 + accumulationRate, years) - 1) / accumulationRate
                    : annualContribution * years;
        }
        double payoutRate = realRate(POST_RETIREMENT_NOMINAL_RATE);
        return calculateAnnuityPayment(existingFv + newFv, payoutRate, payoutYears * 12);
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
        if (taxableIncome <= 100000) return 17406 + (taxableIncome - 50000) * 0.42;
        return 38406 + (taxableIncome - 100000) * 0.45;
    }

    private long calculateStockAsset(SimulationRequestDto req, int years, int payoutYears) {
        if (req.getStockAssetBalance() == 0 && req.getMonthlyStockInvestment() == 0) return 0;
        double accumulationRate = realRate(req.getStockReturnRate());
        double existingFv = req.getStockAssetBalance() * Math.pow(1 + accumulationRate, years);
        double newFv = req.getMonthlyStockInvestment() > 0 && accumulationRate != 0 && years > 0
                ? req.getMonthlyStockInvestment() * 12 * (Math.pow(1 + accumulationRate, years) - 1) / accumulationRate : 0;
        double totalFv = existingFv + newFv;
        double totalCost = req.getStockAssetBalance() + req.getMonthlyStockInvestment() * 12 * years;
        double totalGain = Math.max(0, totalFv - totalCost);
        double annualGain = years > 0 ? totalGain / years : 0;
        double annualTax = Math.max(0, annualGain - STOCK_TAX_EXEMPT) * STOCK_TAX_RATE;
        double fvAfterTax = Math.max(0, totalFv - annualTax * years);

        double payoutRate = realRate(POST_RETIREMENT_NOMINAL_RATE);
        return calculateAnnuityPayment(fvAfterTax, payoutRate, payoutYears * 12);
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
        int militaryCredit = Math.clamp(req.getMilitaryServiceMonths(), 0, MAX_MILITARY_CREDIT_MONTHS) / 12;
        int totalPensionYears = req.getPensionYearsPaid() + years + childCredit + militaryCredit;
        int pensionReceiptAge = determinePensionReceiptAge(req);

        long np = calculateNationalPension(req, totalPensionYears, pensionReceiptAge, age);
        HealthInsuranceResult hi = calculateHealthInsurance(req, np);
        long npAfterTax = Math.max(0, np - hi.total);

        long rpAfterTax = calculateRetirementPension(req, years, payoutYears, age, true);

        long irp = calculateIrpOrPensionSavings(years, irpMonthly * 12, req.getCurrentIrpBalance(), req.getIrpReturnRate(), payoutYears);
        long ps = calculateIrpOrPensionSavings(years, psMonthly * 12, req.getCurrentPensionSavingsBalance(), req.getPensionSavingsReturnRate(), payoutYears);
        long privatePool = irp + ps;
        double privateRate = calculatePrivatePensionTaxRate(age, privatePool);
        long privateAfterTax = Math.round(privatePool * (1 - privateRate));

        long stock = calculateStockAsset(req, years, payoutYears);

        return npAfterTax + rpAfterTax + privateAfterTax + stock;
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

        String contributionPart;
        if (psRemaining > 0 && irpRemaining > 0) {
            contributionPart = "연금저축 월 " + Math.round(psRemaining / 12.0) + MONTHLY_AMOUNT_SUFFIX
                    + " + IRP 월 " + Math.round(irpRemaining / 12.0) + MONTHLY_AMOUNT_SUFFIX;
        } else if (psRemaining > 0) {
            contributionPart = "연금저축을 월 " + Math.round(psRemaining / 12.0) + MONTHLY_AMOUNT_SUFFIX;
        } else {
            contributionPart = "IRP를 월 " + Math.round(irpRemaining / 12.0) + MONTHLY_AMOUNT_SUFFIX;
        }

        return contributionPart + " 더 납입하면 연 " + additionalCredit + "만원을 추가로 돌려받을 수 있습니다.";
    }

    private String generateMessage(long shortfall, SimulationRequestDto req, int estimatedRetirementAge) {
        if (shortfall >= 0) return "축하합니다! 지금 페이스라면 " + estimatedRetirementAge + "세에 은퇴할 수 있습니다. 🎉";
        long neededIrp = req.getMonthlyIrpContribution().longValue() + Math.abs(shortfall);
        return "IRP 납입액을 월 " + req.getMonthlyIrpContribution().longValue() + "만원 → " + neededIrp + "만원으로 늘리면 더 빨리 은퇴할 수 있습니다.";
    }

    private double realRate(double nominalRate) {
        return (1 + nominalRate) / (1 + INFLATION_RATE) - 1;
    }
}