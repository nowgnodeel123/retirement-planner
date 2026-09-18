package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시뮬레이터 정확도 감사(audit).
 *
 * 골든값 테스트(SimulationServiceTest)가 "입력이 같으면 답이 같은가"를 고정한다면,
 * 이 테스트는 "그 답이 맞는 계산인가 / 얼마나 믿을 수 있는가"를 본다.
 * 손계산으로 독립 검증하고, 확률 모델의 재현성과 분포 가정을 실측한다.
 */
class SimulationAccuracyAuditTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService();
        ReflectionTestUtils.setField(simulationService, "shareUrl", "https://x");
        ReflectionTestUtils.setField(simulationService, "aValue", 319.3511);
    }

    private SimulationRequestDto req(int age, double monthlyIncome, int pensionYears,
                                     double irp, double ps, double target,
                                     double stockBalance, double monthlyStock) {
        SimulationRequestDto r = new SimulationRequestDto();
        ReflectionTestUtils.setField(r, "currentAge", age);
        ReflectionTestUtils.setField(r, "monthlyIncome", monthlyIncome);
        ReflectionTestUtils.setField(r, "pensionYearsPaid", pensionYears);
        ReflectionTestUtils.setField(r, "monthlyIrpContribution", irp);
        ReflectionTestUtils.setField(r, "monthlyPensionSavingsContribution", ps);
        ReflectionTestUtils.setField(r, "targetMonthlyExpense", target);
        ReflectionTestUtils.setField(r, "stockAssetBalance", stockBalance);
        ReflectionTestUtils.setField(r, "monthlyStockInvestment", monthlyStock);
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    // 1. 적립 공식 독립 검증 — 코드를 안 보고 금융 공식으로 따로 계산해 맞춘다
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("감사1: IRP 적립액이 연금 미래가치 공식(기말납입)과 일치한다")
    void audit_accumulationMatchesAnnuityFormula() {
        // 34세 → 은퇴까지 굴린 IRP를 손계산과 대조한다.
        // 기존잔액 0, 월 25만원(연 300만원), 연 6%.
        double annual = 300.0, rate = 0.06;

        for (int years : new int[]{10, 20, 30}) {
            // 기말납입 연금 미래가치: PMT × ((1+r)^n − 1) / r
            double expected = annual * (Math.pow(1 + rate, years) - 1) / rate;
            double actual = (double) ReflectionTestUtils.invokeMethod(
                    simulationService, "accumulateFv", 0.0, annual, rate, years);
            assertThat(actual)
                    .as("%d년 적립", years)
                    .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Test
    @DisplayName("감사2: 월납입을 연 1회 기말납입으로 묶어 계산한다 — 실제보다 보수적(과소)으로 나온다")
    void audit_monthlyContributionTreatedAsAnnual_underestimates() {
        double annual = 300.0, rate = 0.10;
        int years = 30;

        double asImplemented = (double) ReflectionTestUtils.invokeMethod(
                simulationService, "accumulateFv", 0.0, annual, rate, years);

        // 실제 월납입(매월 25만원)의 미래가치 — 월 복리로 정확히 계산
        double monthlyRate = Math.pow(1 + rate, 1.0 / 12) - 1;
        int months = years * 12;
        double trueMonthly = (annual / 12) * (Math.pow(1 + monthlyRate, months) - 1) / monthlyRate;

        // 구현값이 실제보다 작다 = 보수적. 틀린 방향은 아니지만 오차는 존재한다.
        assertThat(asImplemented).isLessThan(trueMonthly);
        double errorPercent = (trueMonthly - asImplemented) / trueMonthly * 100;
        System.out.printf("[감사2] 월납입 미반영 과소오차: %.2f%% (30년/연10%%: 구현 %.0f만원 vs 실제 %.0f만원)%n",
                errorPercent, asImplemented, trueMonthly);
        assertThat(errorPercent).isBetween(3.0, 6.0);
    }

    // ────────────────────────────────────────────────────────────────
    // 2. 확률 모델의 재현성 — 같은 입력에 같은 답이 나오는가
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("감사3: 같은 입력은 항상 같은 성공률을 준다(시드 고정 후 — 회귀 방지)")
    void audit_monteCarloIsReproducible() {
        SimulationRequestDto r = req(34, 400.0, 9, 25.0, 50.0, 300.0, 20000.0, 50.0);

        List<Integer> rates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SimulationResponseDto res = simulationService.calculate(r);
            if (res.getMonteCarloResult() != null) {
                rates.add(res.getMonteCarloResult().getSuccessRatePercent());
            }
        }
        if (rates.isEmpty()) {
            System.out.println("[감사3] 몬테카를로 미실행(infeasible) — 판정 불가");
            return;
        }
        int min = rates.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = rates.stream().mapToInt(Integer::intValue).max().orElseThrow();
        System.out.printf("[감사3] 성공률 10회: %s → 최소 %d%%, 최대 %d%%, 폭 %d%%p%n",
                rates, min, max, max - min);
        // 시드 고정 전에는 48~54%로 6%p 흔들렸다. 입력이 같으면 답도 같아야 한다 —
        // 사용자가 새로고침만 해도 "은퇴 성공 확률"이 달라 보이면 안 된다.
        assertThat(max - min).as("같은 입력에 대한 성공률 편차").isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // 3. 분포 가정 — 실제 급락장을 만들어낼 수 있는가
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("감사4: 은퇴 후 수익률 분포(평균3%/표준편차8%)가 리먼·닷컴급 폭락을 만들 수 있는지")
    void audit_crashProbabilityUnderNormalAssumption() {
        double mean = 0.03, sd = 0.08;
        // 역사적 실제 연간 낙폭
        double[][] crashes = {
                {-0.37, 0}, // S&P500 2008 (리먼)
                {-0.41, 0}, // KOSPI 2008
                {-0.49, 0}, // KOSPI 2000 (닷컴)
                {-0.22, 0}, // S&P500 2002
        };
        String[] names = {"S&P500 2008(-37%)", "KOSPI 2008(-41%)", "KOSPI 2000(-49%)", "S&P500 2002(-22%)"};

        System.out.println("[감사4] 정규분포(평균 3%, 표준편차 8%)에서 역사적 폭락이 나올 확률");
        for (int i = 0; i < crashes.length; i++) {
            double z = (crashes[i][0] - mean) / sd;
            double p = normalCdf(z);
            double runsNeeded = p > 0 ? 1.0 / p : Double.POSITIVE_INFINITY;
            System.out.printf("  %-20s z=%.2f  확률=%.3e  → 평균 %.3e 시행마다 1회%n",
                    names[i], z, p, runsNeeded);
        }
        // 1,000회 시행에서 리먼급(-37%)이 한 번이라도 나올 확률
        double pLehman = normalCdf((-0.37 - mean) / sd);
        double atLeastOnce = 1 - Math.pow(1 - pLehman, 1000);
        System.out.printf("  → 1,000회 시행 중 리먼급이 한 번이라도 나올 확률: %.6f%%%n", atLeastOnce * 100);

        // 이 값이 0에 가깝다는 것이 요지 — 모델이 폭락을 구조적으로 못 만든다.
        assertThat(atLeastOnce).isLessThan(0.01);
    }

    @Test
    @DisplayName("감사5: -50% 하한 캡은 이 분포에서 절대 발동하지 않는다(죽은 코드)")
    void audit_minReturnCapNeverTriggers() {
        double z = (-0.5 - 0.03) / 0.08; // -6.6 시그마
        // 꼬리가 워낙 얇아 erf 근사(절대오차 ~1.5e-7)로는 값 자체를 못 잰다.
        // 대신 해석적 상한 P(Z<z) <= exp(-z^2/2) / (|z|*sqrt(2pi)) 로 위에서 누른다.
        double bound = Math.exp(-z * z / 2) / (Math.abs(z) * Math.sqrt(2 * Math.PI));
        System.out.printf("[감사5] -50%% 도달 확률 상한 = %.3e (z=%.2f) — 1,000회 시행 기대 발동 %.3e회%n",
                bound, z, bound * 1000);
        assertThat(bound * 1000).isLessThan(1e-6);
    }

    /** 표준정규 누적분포 — Abramowitz & Stegun 7.1.26 기반 erf 근사. */
    private static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double erf(double x) {
        double sign = Math.signum(x);
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }
}
