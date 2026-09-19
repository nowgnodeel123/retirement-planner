package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시뮬레이터 블랙박스 테스트.
 *
 * 내부 구현(공식·상수·분기)을 모른다고 가정하고, **입력을 바꿨을 때 출력이
 * 상식에 맞게 움직이는가**만 본다. 골든값처럼 특정 숫자를 고정하지 않으므로
 * 공식이 바뀌어도 안 깨지고, 대신 "돈을 더 넣었는데 은퇴가 늦어진다" 같은
 * 방향이 뒤집힌 버그는 확실히 잡는다.
 *
 * 수익률은 전부 명시한다 — 기본값에 기대면 권장 수익률을 조정할 때마다
 * 관계없는 테스트가 깨진다(골든값 테스트에서 실제로 겪었다).
 */
class SimulationBlackBoxTest {

    private SimulationService service;

    @BeforeEach
    void setUp() {
        service = new SimulationService();
        ReflectionTestUtils.setField(service, "shareUrl", "https://x");
        ReflectionTestUtils.setField(service, "aValue", 319.3511);
    }

    /** 기준 입력. 바꾸고 싶은 값만 with*()로 덮어쓴다. */
    private static class Input {
        int age = 34;
        double income = 400;
        int pensionYears = 9;
        double irp = 25, ps = 50, stockMonthly = 50;
        double stockBalance = 20000;
        double target = 300;
        double irpRate = 0.06, dcRate = 0.04, psRate = 0.08, stockRate = 0.10;

        SimulationRequestDto build() {
            SimulationRequestDto r = new SimulationRequestDto();
            ReflectionTestUtils.setField(r, "currentAge", age);
            ReflectionTestUtils.setField(r, "monthlyIncome", income);
            ReflectionTestUtils.setField(r, "pensionYearsPaid", pensionYears);
            ReflectionTestUtils.setField(r, "monthlyIrpContribution", irp);
            ReflectionTestUtils.setField(r, "monthlyPensionSavingsContribution", ps);
            ReflectionTestUtils.setField(r, "monthlyStockInvestment", stockMonthly);
            ReflectionTestUtils.setField(r, "stockAssetBalance", stockBalance);
            ReflectionTestUtils.setField(r, "targetMonthlyExpense", target);
            ReflectionTestUtils.setField(r, "irpReturnRate", irpRate);
            ReflectionTestUtils.setField(r, "pensionReturnRate", dcRate);
            ReflectionTestUtils.setField(r, "pensionSavingsReturnRate", psRate);
            ReflectionTestUtils.setField(r, "stockReturnRate", stockRate);
            return r;
        }
    }

    private SimulationResponseDto run(Input in) {
        return service.calculate(in.build());
    }

    // ── 1. 결정성 ────────────────────────────────────────────────
    @Test
    @DisplayName("BB1: 같은 입력은 몇 번을 돌려도 같은 답을 준다")
    void sameInputSameOutput() {
        Input in = new Input();
        SimulationResponseDto first = run(in);

        for (int i = 0; i < 5; i++) {
            SimulationResponseDto again = run(in);
            assertThat(again.getSummary().getEstimatedRetirementAge())
                    .isEqualTo(first.getSummary().getEstimatedRetirementAge());
            assertThat(again.getSummary().getTotalMonthlyIncome())
                    .isEqualTo(first.getSummary().getTotalMonthlyIncome());
            if (first.getMonteCarloResult() != null) {
                assertThat(again.getMonteCarloResult().getSuccessRatePercent())
                        .as("몬테카를로 성공률까지 동일해야 한다")
                        .isEqualTo(first.getMonteCarloResult().getSuccessRatePercent());
            }
        }
    }

    // ── 2. 단조성: 더 넣으면 더 일찍 ─────────────────────────────
    @Test
    @DisplayName("BB2: 월 납입을 늘리면 은퇴 나이가 빨라지거나 같다")
    void moreContributionNeverDelaysRetirement() {
        Input low = new Input();
        Input high = new Input();
        high.irp = 50; high.ps = 100; high.stockMonthly = 150;

        int lowAge = run(low).getSummary().getEstimatedRetirementAge();
        int highAge = run(high).getSummary().getEstimatedRetirementAge();

        System.out.printf("[BB2] 납입 적음 → %d세 / 납입 많음 → %d세%n", lowAge, highAge);
        assertThat(highAge).isLessThanOrEqualTo(lowAge);
    }

    @Test
    @DisplayName("BB3: 기대 수익률을 올리면 은퇴 나이가 빨라지거나 같다")
    void higherReturnNeverDelaysRetirement() {
        Input low = new Input();
        low.irpRate = 0.03; low.psRate = 0.03; low.stockRate = 0.03;
        Input high = new Input();
        high.irpRate = 0.09; high.psRate = 0.09; high.stockRate = 0.12;

        int lowAge = run(low).getSummary().getEstimatedRetirementAge();
        int highAge = run(high).getSummary().getEstimatedRetirementAge();

        System.out.printf("[BB3] 수익률 낮음 → %d세 / 높음 → %d세%n", lowAge, highAge);
        assertThat(highAge).isLessThanOrEqualTo(lowAge);
    }

    @Test
    @DisplayName("BB4: 목표 생활비를 올리면 은퇴 나이가 늦어지거나 같다")
    void higherTargetNeverAdvancesRetirement() {
        Input cheap = new Input();
        cheap.target = 200;
        Input pricey = new Input();
        pricey.target = 500;

        int cheapAge = run(cheap).getSummary().getEstimatedRetirementAge();
        int priceyAge = run(pricey).getSummary().getEstimatedRetirementAge();

        System.out.printf("[BB4] 목표 200만원 → %d세 / 500만원 → %d세%n", cheapAge, priceyAge);
        assertThat(priceyAge).isGreaterThanOrEqualTo(cheapAge);
    }

    @Test
    @DisplayName("BB5: 기존 자산이 많을수록 은퇴 나이가 빨라지거나 같다")
    void moreExistingAssetsNeverDelaysRetirement() {
        Input poor = new Input();
        poor.stockBalance = 0;
        Input rich = new Input();
        rich.stockBalance = 100000;

        int poorAge = run(poor).getSummary().getEstimatedRetirementAge();
        int richAge = run(rich).getSummary().getEstimatedRetirementAge();

        System.out.printf("[BB5] 자산 0 → %d세 / 자산 10억 → %d세%n", poorAge, richAge);
        assertThat(richAge).isLessThanOrEqualTo(poorAge);
    }

    // ── 3. 응답 자체의 불변식 ────────────────────────────────────
    @Test
    @DisplayName("BB6: 소득 타임라인 길이 = 90세 − 은퇴나이")
    void timelineSpansRetirementToLifeExpectancy() {
        Input in = new Input();
        SimulationResponseDto res = run(in);
        int retireAge = res.getSummary().getEstimatedRetirementAge();

        assertThat(res.getIncomeTimeline()).hasSize(90 - retireAge);
    }

    @Test
    @DisplayName("BB7: 은퇴나이는 항상 현재 나이보다 뒤고, 탐색 상한을 넘지 않는다")
    void retirementAgeStaysInSearchRange() {
        for (int age : new int[]{20, 34, 50, 60, 74}) {
            Input in = new Input();
            in.age = age;
            // 국민연금은 18세부터 낼 수 있으므로 나이에 맞춰 줄인다 —
            // 20세가 9년을 냈다는 입력은 서비스가 거부하는 게 맞다(BB13에서 따로 고정).
            in.pensionYears = Math.min(in.pensionYears, Math.max(0, age - 18));
            int retireAge = run(in).getSummary().getEstimatedRetirementAge();
            assertThat(retireAge).as("현재 %d세", age).isGreaterThan(age);
            assertThat(retireAge).as("현재 %d세", age).isLessThanOrEqualTo(75);
        }
    }

    @Test
    @DisplayName("BB8: 은퇴 시점 목표 생활비는 오늘 금액보다 크다(물가 반영)")
    void targetAtRetirementExceedsTodayValue() {
        SimulationResponseDto res = run(new Input());
        assertThat(res.getSummary().getTargetMonthlyExpenseAtRetirement())
                .isGreaterThan(res.getSummary().getTargetMonthlyExpense());
    }

    @Test
    @DisplayName("BB9: 부족액 = 총소득 − 은퇴시점 목표")
    void shortfallMatchesIncomeMinusTarget() {
        SimulationResponseDto res = run(new Input());
        assertThat(res.getSummary().getMonthlyShortfall())
                .isEqualTo(res.getSummary().getTotalMonthlyIncome()
                        - res.getSummary().getTargetMonthlyExpenseAtRetirement());
    }

    @Test
    @DisplayName("BB10: 세후 소득은 세전 소득을 넘지 않는다")
    void netIncomeNeverExceedsGross() {
        SimulationResponseDto res = run(new Input());
        assertThat(res.getSummary().getTotalMonthlyIncome())
                .isLessThanOrEqualTo(res.getSummary().getTotalMonthlyIncomeGross());
    }

    // ── 4. 월 납입이 실제로 반영되는가(블랙박스 관점) ────────────
    @ParameterizedTest(name = "월 {0}만원")
    @ValueSource(doubles = {0, 10, 50, 100})
    @DisplayName("BB11: 월 납입액이 커질수록 은퇴 시점 적립 총액이 커진다")
    void accumulatedAssetsGrowWithMonthlyContribution(double monthly) {
        Input base = new Input();
        base.stockMonthly = 0; base.irp = 0; base.ps = 0;
        long baseTotal = run(base).getAccumulatedAssets().getTotal();

        Input in = new Input();
        in.stockMonthly = monthly; in.irp = 0; in.ps = 0;
        long total = run(in).getAccumulatedAssets().getTotal();

        if (monthly == 0) {
            assertThat(total).isEqualTo(baseTotal);
        } else {
            assertThat(total).isGreaterThan(baseTotal);
        }
    }

    @Test
    @DisplayName("BB13: 나이에 비해 불가능한 국민연금 납입기간은 거부한다")
    void rejectsImpossiblePensionYears() {
        Input in = new Input();
        in.age = 20;
        in.pensionYears = 9; // 18세부터 내도 최대 2년

        assertThatThrownBy(() -> run(in))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("국민연금 납입 기간");
    }

    @Test
    @DisplayName("BB12: 극단 입력에서도 예외 없이 응답한다")
    void extremeInputsDoNotThrow() {
        Input zero = new Input();
        zero.irp = 0; zero.ps = 0; zero.stockMonthly = 0; zero.stockBalance = 0;
        zero.pensionYears = 0; zero.income = 0; zero.target = 1;
        assertThat(run(zero)).isNotNull();

        Input huge = new Input();
        huge.income = 10000; huge.stockBalance = 1_000_000; huge.stockMonthly = 1000;
        huge.target = 10000;
        assertThat(run(huge)).isNotNull();
    }
}
