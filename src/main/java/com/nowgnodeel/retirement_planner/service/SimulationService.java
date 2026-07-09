package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 은퇴 가능 나이 시뮬레이션.
 *
 * <p>단위 원칙(중요): 모든 금액은 "명목(nominal)" 기준으로 통일한다.
 * <ul>
 *   <li>target(목표 생활비): 매년 인플레이션(2.5%)만큼 커진다 — 미래 시점 실제 필요 금액</li>
 *   <li>national(국민연금): 수령 개시 후 매년 물가연동으로 커진다 (실제 제도 반영)</li>
 *   <li>mid(퇴직연금+IRP+연금저축): 입력받은 명목수익률로 적립 → 55세부터 평평한 명목 연금</li>
 *   <li>liquid(주식/ETF): 입력받은 명목수익률로 적립 → 은퇴 후 3% 명목수익률로 전환</li>
 * </ul>
 * 자산 쪽에 realRate(실질 전환)를 적용하면 target(명목)과 단위가 어긋나
 * gap 계산이 무의미해지므로 절대 섞지 않는다.
 */
@Service
public class SimulationService {

    // ── 거시 가정 ──
    private static final double INFLATION_RATE = 0.025;
    private static final int LIFE_EXPECTANCY = 90;
    private static final double POST_RETIREMENT_NOMINAL_RATE = 0.03;

    // ── 3구간 gap-filling: MID(퇴직연금+IRP+연금저축) unlock 나이 ──
    private static final int MID_UNLOCK_AGE = 55;

    // ── 최소 은퇴나이 탐색 상한 ──
    private static final int MAX_SEARCH_AGE = 75;

    // ── 생애주기 임금 성장 ──
    private static final int GROWTH_PHASE_END_AGE = 44;
    private static final int SLOWDOWN_PHASE_END_AGE = 49;
    private static final int PLATEAU_PHASE_END_AGE = 59;
    private static final double GROWTH_RATE = 0.05;
    private static final double SLOWDOWN_RATE = 0.01;
    private static final double PLATEAU_RATE = 0.0;
    private static final double DECLINE_RATE = -0.02;

    // ── 국민연금 ──
    // WHY 0.1075: 2026.1.1 이후 노령연금 수급권 취득자 비례상수 1.29(소득대체율 43%,
    // 2025년 연금개혁 반영)를 월 단위로 환산한 값(1.29/12). 출처: 국민연금공단 크레딧 제도 안내.
    private static final double INCOME_REPLACEMENT_COEFFICIENT = 0.1075;
    private static final double NATIONAL_PENSION_INCOME_CAP = 637.0;
    private static final int NATIONAL_PENSION_NORMAL_AGE = 65;
    private static final int NATIONAL_PENSION_EARLY_MIN_AGE = 60;
    private static final int NATIONAL_PENSION_LATE_MAX_AGE = 70;
    private static final int MAX_MILITARY_CREDIT_MONTHS = 12;

    // ── 사적연금 과세 ──
    private static final double PRIVATE_PENSION_SEPARATE_TAX_LIMIT = 1500.0;
    private static final double PRIVATE_PENSION_SEPARATE_TAX_RATE = 0.15;
    private static final double PENSION_RECEIPT_PAYMENT_RATE_WITHIN_10Y = 0.70;
    private static final double PENSION_RECEIPT_PAYMENT_RATE_AFTER_10Y = 0.60;

    // ── 세액공제 ──
    private static final double PENSION_SAVINGS_TAX_LIMIT = 600.0;
    private static final double IRP_TAX_LIMIT = 300.0;
    private static final double IRP_PENSION_COMBINED_LIMIT = 900.0;
    private static final double TAX_CREDIT_RATE_LOW = 0.165;
    private static final double TAX_CREDIT_RATE_HIGH = 0.132;
    private static final double INCOME_THRESHOLD = 5500.0;

    // ── 건강보험 ──
    private static final double HEALTH_INSURANCE_RATE = 0.0709;
    private static final double NATIONAL_PENSION_INCOME_RATIO = 0.5;
    private static final double PROPERTY_DEDUCTION = 10000.0;
    private static final double PROPERTY_INSURANCE_RATE = 0.000911;

    // ── 주식 양도세 ──
    private static final double STOCK_TAX_RATE = 0.22;
    private static final double STOCK_TAX_EXEMPT = 250.0;

    private static final String MONTHLY_AMOUNT_SUFFIX = "만원";

    @Value("${app.share-url}")
    private String shareUrl;

    @Value("${app.national-pension.a-value}")
    private double aValue;

    // ======================================================================
    // 메인 진입점
    // ======================================================================

    public SimulationResponseDto calculate(SimulationRequestDto req) {
        // WHY: 필드 단위 @Min/@Max로는 잡을 수 없는 필드 간 모순.
        // 28세가 40년 납입 같은 입력은 크래시는 없지만 무의미한 결과를 낳는다. (검토 Q-1)
        if (req.getPensionYearsPaid() > req.getCurrentAge() - 18) {
            throw new IllegalArgumentException(
                    "국민연금 납입 기간이 나이에 비해 너무 길어요. (최대 " + (req.getCurrentAge() - 18) + "년)");
        }

        RetirementSearchResult search = findEarliestRetirementAge(req);
        int estimatedRetirementAge = search.age();
        FirstYearBreakdown fy = search.projection().firstYearBreakdown();

        int yearsUntilRetirement = estimatedRetirementAge - req.getCurrentAge();
        int totalPensionYears = calculateTotalPensionYears(req, yearsUntilRetirement);
        int pensionReceiptAge = determinePensionReceiptAge(req);

        long totalGross = fy.nationalGross() + fy.midGross() + fy.liquidWithdrawalGross();
        long totalAfterTax = fy.nationalAfterTax() + fy.midAfterTax() + fy.liquidWithdrawalAfterTax();
        long target = Math.round(req.getTargetMonthlyExpense());
        long shortfall = totalAfterTax - target;

        String message = generateMessage(estimatedRetirementAge, search.feasible());
        String shareMessage = "시뮬레이션 해보니 " + estimatedRetirementAge + "세 은퇴 가능성이 나왔어! 너는? → " + shareUrl;

        List<SimulationResponseDto.YearlyIncomePoint> timeline = toResponseTimeline(
                search.projection().timeline());

        return SimulationResponseDto.builder()
                .summary(SimulationResponseDto.Summary.builder()
                        .totalMonthlyIncome(totalAfterTax)
                        .totalMonthlyIncomeGross(totalGross)
                        .targetMonthlyExpense(target)
                        .monthlyShortfall(shortfall)
                        .estimatedRetirementAge(estimatedRetirementAge)
                        .feasible(search.feasible())
                        .message(message)
                        .shareMessage(shareMessage)
                        .build())
                .breakdown(SimulationResponseDto.Breakdown.builder()
                        .nationalPension(fy.nationalAfterTax())
                        .retirementPension(fy.midAfterTax())
                        .retirementPensionGross(fy.midGross())
                        .irp(0)
                        .irpGross(0)
                        .pensionSavings(0)
                        .pensionSavingsGross(0)
                        .pensionSavingsTaxBenefit(calculatePensionSavingsTaxBenefit(req.getMonthlyPensionSavingsContribution()))
                        .stockAsset(fy.liquidWithdrawalAfterTax())
                        .build())
                .taxDetail(SimulationResponseDto.TaxDetail.builder()
                        .pensionIncomeTaxRate(fy.midTaxRate() * 100)
                        .healthInsuranceRate(HEALTH_INSURANCE_RATE * 100)
                        .monthlyPensionTax(fy.midGross() - fy.midAfterTax())
                        .monthlyHealthInsurance(fy.nationalGross() - fy.nationalAfterTax())
                        .totalMonthlyTax((fy.midGross() - fy.midAfterTax()) + (fy.nationalGross() - fy.nationalAfterTax()))
                        .isPreciseHealthInsurance(req.isUsePreciseHealthInsurance())
                        .healthInsuranceIncomePart(fy.nationalGross() - fy.nationalAfterTax())
                        .healthInsurancePropertyPart(0)
                        .propertyDeductionApplied(0)
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
                .incomeTimeline(timeline)
                .build();
    }

    private List<SimulationResponseDto.YearlyIncomePoint> toResponseTimeline(List<YearlyIncome> internal) {
        List<SimulationResponseDto.YearlyIncomePoint> result = new ArrayList<>(internal.size());
        for (YearlyIncome y : internal) {
            result.add(SimulationResponseDto.YearlyIncomePoint.builder()
                    .age(y.age())
                    .nationalAfterTax(y.nationalAfterTax())
                    .midAfterTax(y.midAfterTax())
                    .liquidWithdrawalAfterTax(y.liquidAfterTax())
                    .targetExpense(y.targetExpense())
                    .build());
        }
        return result;
    }

    // ======================================================================
    // 최소 은퇴나이 탐색
    // ======================================================================

    private record RetirementSearchResult(int age, boolean feasible, RetirementProjection projection) {}

    /**
     * currentAge+1부터 한 살씩 올려가며 처음으로 실현 가능한 나이를 찾는다.
     * WHY: feasible 여부는 나이에 대해 단조증가(늦게 은퇴할수록 쉬움)하므로
     *      첫 true가 곧 "가장 이른 은퇴 나이"다. 상한(75세)까지 못 찾으면
     *      infeasible로 표시하고 75세 결과를 참고용으로 돌려준다.
     */
    private RetirementSearchResult findEarliestRetirementAge(SimulationRequestDto req) {
        RetirementProjection last = null;
        for (int age = req.getCurrentAge() + 1; age <= MAX_SEARCH_AGE; age++) {
            last = projectRetirement(req, age);
            if (last.feasible()) {
                return new RetirementSearchResult(age, true, last);
            }
        }
        return new RetirementSearchResult(MAX_SEARCH_AGE, false, last);
    }

    // ======================================================================
    // 3구간 gap-filling 시뮬레이션
    // ======================================================================

    private record FirstYearBreakdown(
            long nationalGross, long nationalAfterTax,
            long midGross, long midAfterTax, double midTaxRate,
            long liquidWithdrawalGross, long liquidWithdrawalAfterTax
    ) {}

    /** 특정 나이 시점의 세후 월 소득 구성 한 점. 결과 화면 타임라인 차트용. */
    private record YearlyIncome(
            int age, long nationalAfterTax, long midAfterTax, long liquidAfterTax, long targetExpense
    ) {}

    private record RetirementProjection(
            boolean feasible, FirstYearBreakdown firstYearBreakdown, List<YearlyIncome> timeline
    ) {}

    /** 후보 나이 하나가 실현 가능한지 + 1년차 소득 구성 + 연도별 타임라인을 함께 계산한다. */
    private RetirementProjection projectRetirement(SimulationRequestDto req, int candidateAge) {
        int yearsUntilRetirement = candidateAge - req.getCurrentAge();
        int totalPensionYears = calculateTotalPensionYears(req, yearsUntilRetirement);
        int pensionReceiptAge = determinePensionReceiptAge(req);

        // ── MID: 55세(또는 그 이후 은퇴 시점)까지 적립 후 연금화 ──
        MidAssets midAtUnlock = accumulateMidToUnlock(req, candidateAge);

        // WHY: 55세 이후 은퇴자는 은퇴 시점부터 90세까지만 나눠 받는다.
        //      55세로 고정하면 지급기간이 부풀어 월 지급액이 과소 계산된다.
        int payoutStartAge = Math.max(candidateAge, MID_UNLOCK_AGE);
        int midPayoutYears = LIFE_EXPECTANCY - payoutStartAge;
        double payoutRate = POST_RETIREMENT_NOMINAL_RATE;

        // WHY: 퇴직소득세 근속연수는 "총 경력"(과거+미래) 기준이다.
        long totalServiceYears = Math.max(1, req.getYearsOfService() + yearsUntilRetirement);
        double retirementLumpAfterTax = applyRetirementIncomeTax(midAtUnlock.dbOrDcLump(), totalServiceYears, midPayoutYears);
        long retirementPensionGross = calculateAnnuityPayment(midAtUnlock.dbOrDcLump(), payoutRate, midPayoutYears * 12);
        long retirementPensionAfterTax = calculateAnnuityPayment(retirementLumpAfterTax, payoutRate, midPayoutYears * 12);

        long irpGross = calculateAnnuityPayment(midAtUnlock.irpFv(), payoutRate, midPayoutYears * 12);
        long psGross = calculateAnnuityPayment(midAtUnlock.psFv(), payoutRate, midPayoutYears * 12);
        long privatePoolGross = irpGross + psGross;
        double privateTaxRate = calculatePrivatePensionTaxRate(payoutStartAge, privatePoolGross);
        long irpAfterTax = Math.round(irpGross * (1 - privateTaxRate));
        long psAfterTax = Math.round(psGross * (1 - privateTaxRate));

        long midMonthlyGrossTotal = retirementPensionGross + irpGross + psGross;
        long midMonthlyAfterTaxTotal = retirementPensionAfterTax + irpAfterTax + psAfterTax;

        // ── NATIONAL: receiptAge부터 물가연동 지급 ──
        long nationalMonthlyGross = calculateNationalPension(req, totalPensionYears, pensionReceiptAge, candidateAge);
        HealthInsuranceResult hi = calculateHealthInsurance(req, nationalMonthlyGross);
        long nationalMonthlyAfterTax = Math.max(0, nationalMonthlyGross - hi.total);

        // ── LIQUID: 은퇴나이부터 90세까지 gap-filling drawdown ──
        double liquidAtRetirement = accumulateFv(
                req.getStockAssetBalance(),
                req.getMonthlyStockInvestment() * 12,
                req.getStockReturnRate(),
                yearsUntilRetirement);
        double costBasis = req.getStockAssetBalance() + req.getMonthlyStockInvestment() * 12 * yearsUntilRetirement;
        LiquidPortfolio liquid = new LiquidPortfolio(liquidAtRetirement, costBasis);

        boolean feasible = true;
        long firstYearLiquidGross = 0;
        long firstYearLiquidAfterTax = 0;
        List<YearlyIncome> timeline = new ArrayList<>();

        for (int age = candidateAge; age < LIFE_EXPECTANCY; age++) {
            int yearsFromNow = age - req.getCurrentAge();
            double targetAnnual = req.getTargetMonthlyExpense() * 12 * Math.pow(1 + INFLATION_RATE, yearsFromNow);

            double midAnnual = age >= MID_UNLOCK_AGE ? midMonthlyAfterTaxTotal * 12 : 0;

            double nationalAnnual = 0;
            if (age >= pensionReceiptAge) {
                int yearsSinceStart = age - pensionReceiptAge;
                nationalAnnual = nationalMonthlyAfterTax * 12 * Math.pow(1 + INFLATION_RATE, yearsSinceStart);
            }

            double gapNet = Math.max(0, targetAnnual - midAnnual - nationalAnnual);

            long yearLiquidGross = 0;
            if (gapNet > 0) {
                Optional<Double> grossSale = liquid.withdrawNet(gapNet);
                if (grossSale.isEmpty()) {
                    feasible = false;
                    break;
                }
                yearLiquidGross = Math.round(grossSale.get());
            }

            long monthlyLiquidAfterTax = Math.round(gapNet > 0 ? gapNet / 12.0 : 0);

            // WHY: 결과 화면 차트가 쓸 연도별 스냅샷. 세후 월 금액으로 통일해서 저장한다.
            timeline.add(new YearlyIncome(
                    age,
                    age >= pensionReceiptAge ? Math.round(nationalAnnual / 12.0) : 0,
                    age >= MID_UNLOCK_AGE ? Math.round(midAnnual / 12.0) : 0,
                    monthlyLiquidAfterTax,
                    Math.round(targetAnnual / 12.0)
            ));

            // Case A: 인출 후 남은 잔고만 은퇴 후 수익률(3% 명목)로 성장
            liquid.grow(payoutRate);

            if (age == candidateAge) {
                firstYearLiquidGross = Math.round(yearLiquidGross / 12.0);
                firstYearLiquidAfterTax = monthlyLiquidAfterTax;
            }
        }

        FirstYearBreakdown fy = new FirstYearBreakdown(
                candidateAge >= pensionReceiptAge ? nationalMonthlyGross : 0,
                candidateAge >= pensionReceiptAge ? nationalMonthlyAfterTax : 0,
                candidateAge >= MID_UNLOCK_AGE ? midMonthlyGrossTotal : 0,
                candidateAge >= MID_UNLOCK_AGE ? midMonthlyAfterTaxTotal : 0,
                privateTaxRate,
                firstYearLiquidGross,
                firstYearLiquidAfterTax
        );

        return new RetirementProjection(feasible, fy, timeline);
    }

    // ======================================================================
    // LIQUID 자산 캡슐화
    // ======================================================================

    /**
     * 주식/ETF 포트폴리오의 잔고·취득원가·양도세 gross-up을 한곳에서 관리한다.
     * WHY: 잔고와 원가는 항상 같은 비율로 함께 줄어야 하는 불변식이 있는데,
     *      루프 안에 흩어져 있으면 이 불변식이 깨지기 쉽다.
     */
    private static final class LiquidPortfolio {
        private double balance;
        private double costBasis;

        LiquidPortfolio(double balance, double costBasis) {
            this.balance = balance;
            this.costBasis = costBasis;
        }

        /**
         * 세후 순금액 neededNet이 손에 남도록 매도한다.
         * @return 매도한 총액(세전). 잔고가 부족하면 Optional.empty()
         */
        Optional<Double> withdrawNet(double neededNet) {
            double gainRatio = balance > 0 ? Math.max(0, 1 - costBasis / balance) : 0;
            double grossSale = grossUpForStockTax(neededNet, gainRatio);

            if (grossSale > balance) {
                return Optional.empty();
            }

            double soldRatio = balance > 0 ? grossSale / balance : 0;
            costBasis = Math.max(0, costBasis * (1 - soldRatio));
            balance -= grossSale;
            return Optional.of(grossSale);
        }

        void grow(double annualRate) {
            balance *= (1 + annualRate);
        }

        /**
         * 양도소득세(22%, 연 250만원 공제) gross-up.
         * WHY: 필요한 순금액이 정해져 있을 때, 세금 뗀 후에도 그 금액이
         *      남도록 역산한다. 공제 구간 때문에 2단계로 나뉜다.
         */
        private static double grossUpForStockTax(double neededNet, double gainRatio) {
            double gain0 = neededNet * gainRatio;
            if (gain0 <= STOCK_TAX_EXEMPT) return neededNet;

            double numerator = neededNet - STOCK_TAX_RATE * STOCK_TAX_EXEMPT;
            double denominator = 1 - STOCK_TAX_RATE * gainRatio;
            return numerator / denominator;
        }
    }

    // ======================================================================
    // MID 자산 적립
    // ======================================================================

    private record MidAssets(double dbOrDcLump, double irpFv, double psFv) {}

    /**
     * MID 자산을 unlock 시점까지 굴린다.
     * 은퇴나이가 55세 이전이면 잠긴 채로 입력 수익률(명목)로 계속 성장한다.
     */
    private MidAssets accumulateMidToUnlock(SimulationRequestDto req, int candidateAge) {
        int yearsUntilRetirement = candidateAge - req.getCurrentAge();

        double dbOrDcLump = calculateRetirementLumpSum(req, yearsUntilRetirement, candidateAge);
        double irpFv = accumulateFv(req.getCurrentIrpBalance(), req.getMonthlyIrpContribution() * 12,
                req.getIrpReturnRate(), yearsUntilRetirement);
        double psFv = accumulateFv(req.getCurrentPensionSavingsBalance(), req.getMonthlyPensionSavingsContribution() * 12,
                req.getPensionSavingsReturnRate(), yearsUntilRetirement);

        if (candidateAge < MID_UNLOCK_AGE) {
            int lockedYears = MID_UNLOCK_AGE - candidateAge;
            // DC형만 계속 수익률로 굴림. DB형은 이미 확정된 목돈이라 그대로 유지.
            if (!"DB".equals(req.getPensionType())) {
                dbOrDcLump *= Math.pow(1 + req.getPensionReturnRate(), lockedYears);
            }
            irpFv *= Math.pow(1 + req.getIrpReturnRate(), lockedYears);
            psFv *= Math.pow(1 + req.getPensionSavingsReturnRate(), lockedYears);
        }

        return new MidAssets(dbOrDcLump, irpFv, psFv);
    }

    private double accumulateFv(double currentBalance, double annualContribution, double rate, int years) {
        double existingFv = currentBalance > 0 ? currentBalance * Math.pow(1 + rate, years) : 0;
        double newFv = 0;
        if (annualContribution > 0 && years > 0) {
            newFv = rate != 0
                    ? annualContribution * (Math.pow(1 + rate, years) - 1) / rate
                    : annualContribution * years;
        }
        return existingFv + newFv;
    }

    /**
     * 퇴직연금 목돈 계산.
     * DB형: 최종월급 × 총 근속연수(과거 yearsOfService + 미래).
     * DC형: 기존 잔액을 굴린 값 + 매년 월급 1개월치 적립분을 굴린 값.
     */
    private double calculateRetirementLumpSum(SimulationRequestDto req, int years, int retirementAge) {
        if ("DB".equals(req.getPensionType())) {
            double finalSalary = projectSalaryAtAge(req.getMonthlyIncome(), req.getCurrentAge(), retirementAge);
            int totalServiceYears = req.getYearsOfService() + years;
            return finalSalary * totalServiceYears;
        }

        double rate = req.getPensionReturnRate();
        // WHY: 기존 DC 잔액도 은퇴 시점까지 같은 수익률로 계속 굴러간다.
        double fv = req.getDcCurrentBalance() * Math.pow(1 + rate, years);
        for (int i = 0; i < years; i++) {
            int contributionAge = req.getCurrentAge() + i;
            double annualContribution = projectSalaryAtAge(req.getMonthlyIncome(), req.getCurrentAge(), contributionAge);
            int remainingYears = years - i;
            fv += annualContribution * Math.pow(1 + rate, remainingYears);
        }
        return fv;
    }

    // ======================================================================
    // 생애주기 임금
    // ======================================================================

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

    private double annualGrowthRateAt(int age) {
        if (age <= GROWTH_PHASE_END_AGE) return GROWTH_RATE;
        if (age <= SLOWDOWN_PHASE_END_AGE) return SLOWDOWN_RATE;
        if (age <= PLATEAU_PHASE_END_AGE) return PLATEAU_RATE;
        return DECLINE_RATE;
    }

    // ======================================================================
    // 건강보험
    // ======================================================================

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

    // ======================================================================
    // 국민연금
    // ======================================================================

    private int determinePensionReceiptAge(SimulationRequestDto req) {
        if ("EARLY".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.clamp(req.getNationalPensionReceiptAge(), NATIONAL_PENSION_EARLY_MIN_AGE, NATIONAL_PENSION_NORMAL_AGE - 1);
        }
        if ("LATE".equals(req.getNationalPensionReceiptType()) && req.getNationalPensionReceiptAge() != null) {
            return Math.clamp(req.getNationalPensionReceiptAge(), NATIONAL_PENSION_NORMAL_AGE + 1, NATIONAL_PENSION_LATE_MAX_AGE);
        }
        return NATIONAL_PENSION_NORMAL_AGE;
    }

    /**
     * 출산크레딧 추가 가입기간(개월).
     * WHY: 2026.1.1 시행 개정 국민연금법 기준 — 첫째·둘째 각 12개월,
     * 셋째부터 자녀 1명당 18개월, 50개월 상한 폐지. (국민연금공단 공식 안내)
     * 셋째부터 18개월(1.5년)이라 연 단위 정수로는 표현이 안 되므로 개월로 계산한다.
     */
    private int calculateChildCreditMonths(int childrenCount) {
        if (childrenCount <= 0) return 0;
        int firstTwo = Math.min(childrenCount, 2) * 12;
        int thirdAndBeyond = Math.max(0, childrenCount - 2) * 18;
        return firstTwo + thirdAndBeyond;
    }

    /** 크레딧(출산+군복무)을 포함한 총 가입연수. 개월 합산 후 연 단위로 내림. */
    private int calculateTotalPensionYears(SimulationRequestDto req, int yearsUntilRetirement) {
        int creditMonths = calculateChildCreditMonths(req.getChildrenCount())
                + Math.clamp(req.getMilitaryServiceMonths(), 0, MAX_MILITARY_CREDIT_MONTHS);
        return req.getPensionYearsPaid() + yearsUntilRetirement + creditMonths / 12;
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

    // ======================================================================
    // 퇴직소득세
    // ======================================================================

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

    // ======================================================================
    // 사적연금 소득세
    // ======================================================================

    private double calculatePrivatePensionTaxRate(int receiptAge, long monthlyPrivatePensionGross) {
        long annualIncome = monthlyPrivatePensionGross * 12;

        if (annualIncome <= PRIVATE_PENSION_SEPARATE_TAX_LIMIT) {
            if (receiptAge < 70) return 0.055;
            if (receiptAge < 80) return 0.044;
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

    private long calculateAnnuityPayment(double fv, double annualRate, int months) {
        if (fv <= 0 || months <= 0) return 0;
        double monthlyRate = annualRate / 12;
        if (monthlyRate <= 0) return Math.round(fv / months);
        return Math.round(fv * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months)));
    }

    // ======================================================================
    // 세액공제
    // ======================================================================

    private long calculatePensionSavingsTaxBenefit(double monthlyContribution) {
        return Math.round(Math.min(monthlyContribution * 12, PENSION_SAVINGS_TAX_LIMIT) * TAX_CREDIT_RATE_LOW);
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

    private String generateMessage(int estimatedRetirementAge, boolean feasible) {
        if (!feasible) {
            return "지금 페이스로는 " + MAX_SEARCH_AGE + "세까지도 목표 생활비를 채우기 어려워요. 납입액이나 목표를 조정해보세요.";
        }
        // WHY: "은퇴할 수 있습니다"는 결과 보장으로 읽힐 수 있는 단정 표현이라
        // 컴플라이언스 관점에서 추정 표현으로 완화한다. (심사 TOP10 #1)
        return "지금 페이스가 유지된다면 " + estimatedRetirementAge + "세에 은퇴가 가능할 것으로 추정돼요.";
    }
}