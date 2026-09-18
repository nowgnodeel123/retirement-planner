package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특성화 테스트(characterization test) — M14 BigDecimal 전환 착수 전 현재(double 기반)
 * 동작을 골든값으로 고정해둔다. 이전 테스트(src/test/java/service/SimulationServiceTest.java)는
 * 패키지 위치가 어긋나 있었고, D-114(역산 전환) 이후 더 이상 입력값이 아닌 retirementAge 필드를
 * 리플렉션으로 설정하려다 매번 실패하는 상태였다(M14/D-162 세션에서 발견, 백로그 항목 해소).
 * 골든값은 실제 코드를 그대로 실행해 뽑은 값으로, 세법·연금 공식의 정답을 검증하는 게
 * 아니라 "리팩터링 전후 결과가 바뀌지 않았는지"를 잡아내는 회귀 방지 용도다.
 */
class SimulationServiceTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService();
        ReflectionTestUtils.setField(simulationService, "shareUrl", "https://retirement-planner.vercel.app");
        ReflectionTestUtils.setField(simulationService, "aValue", 319.3511);
    }

    private SimulationRequestDto request(int currentAge, double monthlyIncome, int pensionYearsPaid,
                                          double monthlyIrp, double monthlyPensionSavings,
                                          double targetMonthlyExpense) {
        SimulationRequestDto req = new SimulationRequestDto();
        ReflectionTestUtils.setField(req, "currentAge", currentAge);
        ReflectionTestUtils.setField(req, "monthlyIncome", monthlyIncome);
        ReflectionTestUtils.setField(req, "pensionYearsPaid", pensionYearsPaid);
        ReflectionTestUtils.setField(req, "monthlyIrpContribution", monthlyIrp);
        ReflectionTestUtils.setField(req, "monthlyPensionSavingsContribution", monthlyPensionSavings);
        ReflectionTestUtils.setField(req, "targetMonthlyExpense", targetMonthlyExpense);

        // 수익률을 명시한다. 예전엔 DTO 필드 기본값에 기대고 있었는데, 그러면 화면에서
        // 권장 수익률을 조정할 때마다 계산 공식은 그대로인데도 골든값이 통째로 깨진다
        // (실제로 기본값을 5/4/6/7 → 6/4/8/10으로 올리자 이 파일에서만 3건이 깨졌다).
        // 골든 테스트가 고정하려는 건 "입력이 같으면 답이 같은가"이므로 입력은 전부
        // 테스트가 들고 있어야 한다. 아래 값은 골든값을 뽑을 당시의 기본값이다.
        ReflectionTestUtils.setField(req, "irpReturnRate", 0.05);
        ReflectionTestUtils.setField(req, "pensionReturnRate", 0.04);
        ReflectionTestUtils.setField(req, "pensionSavingsReturnRate", 0.06);
        ReflectionTestUtils.setField(req, "stockReturnRate", 0.07);
        return req;
    }

    @Test
    @DisplayName("시나리오 A(30세/소득400/국민연금10년/IRP30/연금저축20/목표250) 골든값 고정")
    void scenarioA_goldenValues() {
        SimulationResponseDto res = simulationService.calculate(
                request(30, 400.0, 10, 30.0, 20.0, 250.0));

        assertThat(res.getSummary().getEstimatedRetirementAge()).isEqualTo(69);
        assertThat(res.getSummary().isFeasible()).isTrue();
        // 915 → 938: 국민연금을 은퇴 첫해(69세) 기준으로 맞추면서 올랐다. 예전에는 요약이
        // 수급 개시(65세) 시점 값을 쓰고 차트만 물가연동분을 반영해, 같은 해를 두고 두
        // 숫자가 동시에 보였다.
        assertThat(res.getSummary().getTotalMonthlyIncome()).isEqualTo(938);
        // 목표는 입력값(오늘 250만원)과 은퇴 시점 환산값을 함께 내려준다. 명목 소득에서
        // 오늘 기준 목표를 빼던 예전 방식은 없는 여유를 만들어냈다.
        assertThat(res.getSummary().getTargetMonthlyExpense()).isEqualTo(250);
        assertThat(res.getSummary().getTargetMonthlyExpenseAtRetirement()).isEqualTo(655);
        assertThat(res.getSummary().getMonthlyShortfall()).isEqualTo(938 - 655);
        // 219 → 242: 수급 개시(65세)가 아니라 은퇴 첫해(69세) 기준으로 통일한 결과다.
        // 4년치 물가연동(1.025^4 = 1.104)이 반영돼 차트 첫 점과 같은 값이 됐다.
        assertThat(res.getBreakdown().getNationalPension()).isEqualTo(242);
        // 696은 퇴직연금·IRP·연금저축을 합친 값이었고 IRP·연금저축은 0으로 내려갔다.
        // 셋으로 쪼갠 합이 예전 값과 같은지까지 고정해둔다(330 + 198 + 168 = 696).
        assertThat(res.getBreakdown().getRetirementPension()).isEqualTo(330);
        assertThat(res.getBreakdown().getIrp()).isEqualTo(198);
        assertThat(res.getBreakdown().getPensionSavings()).isEqualTo(168);
        assertThat(res.getBreakdown().getRetirementPension()
                + res.getBreakdown().getIrp()
                + res.getBreakdown().getPensionSavings()).isEqualTo(696);
        // 은퇴 시점 적립 총액(만원). 연금 계열 기준 나이는 max(은퇴나이, 55).
        assertThat(res.getAccumulatedAssets().getTotal()).isEqualTo(140055);
        assertThat(res.getAccumulatedAssets().getPensionUnlockAge()).isEqualTo(69);
        assertThat(res.getMeta().getYearsUntilRetirement()).isEqualTo(39);
        assertThat(res.getIncomeTimeline()).hasSize(21);
    }

    @Test
    @DisplayName("시나리오 B(45세/소득600/국민연금20년/IRP50/연금저축40/목표400, 주식자산 0) — 검색상한(75세)에서도 infeasible")
    void scenarioB_noStockBuffer_isInfeasibleEvenWithHighLaterIncome() {
        SimulationResponseDto res = simulationService.calculate(
                request(45, 600.0, 20, 50.0, 40.0, 400.0));

        // WHY: 은퇴~퇴직연금 개시 전(구간1)을 메울 주식/ETF 잔액이 0이라, 첫해 소득만 보면
        // 목표를 넘겨도(총소득982 > 은퇴시점 목표839) 90세까지 버티는 나이를 못 찾아 infeasible이 된다.
        // "feasible"이 첫해 흑자가 아니라 90세까지의 지속가능성을 뜻한다는 걸 고정해두는 케이스.
        assertThat(res.getSummary().getEstimatedRetirementAge()).isEqualTo(75);
        assertThat(res.getSummary().isFeasible()).isFalse();
        assertThat(res.getSummary().getTotalMonthlyIncome()).isEqualTo(982); // 922 → 시나리오 A와 같은 이유
        assertThat(res.getSummary().getTargetMonthlyExpenseAtRetirement()).isEqualTo(839);
        assertThat(res.getIncomeTimeline()).hasSize(10);
    }

    @Test
    @DisplayName("시나리오 C(28세/소득300/국민연금5년/사적연금·주식 전무/목표500) — infeasible, 소득타임라인 비어있음")
    void scenarioC_noPension_noStock_isInfeasible() {
        SimulationResponseDto res = simulationService.calculate(
                request(28, 300.0, 5, 0.0, 0.0, 500.0));

        assertThat(res.getSummary().getEstimatedRetirementAge()).isEqualTo(75);
        assertThat(res.getSummary().isFeasible()).isFalse();
        assertThat(res.getIncomeTimeline()).isEmpty();
    }

    @Test
    @DisplayName("연금 소득이 낮으면 건강보험 피부양자 위험 없음으로 추정한다 (M15/D-168)")
    void dependentStatusWarning_lowPensionIncome_notAtRisk() {
        SimulationResponseDto res = simulationService.calculate(
                request(59, 200.0, 10, 0.0, 0.0, 100.0));

        var warning = res.getDependentStatusWarning();
        assertThat(warning.isAtRisk()).isFalse();
        assertThat(warning.getEstimatedAnnualIncome()).isEqualTo(1176);
        assertThat(warning.getThresholdAnnualIncome()).isEqualTo(2000);
    }

    @Test
    @DisplayName("연금 소득이 높으면 건강보험 피부양자 위험 있음으로 추정한다 (M15/D-168)")
    void dependentStatusWarning_highPensionIncome_atRisk() {
        SimulationResponseDto res = simulationService.calculate(
                request(30, 400.0, 10, 30.0, 20.0, 250.0));

        var warning = res.getDependentStatusWarning();
        assertThat(warning.isAtRisk()).isTrue();
        assertThat(warning.getEstimatedAnnualIncome()).isEqualTo(11724);
        assertThat(warning.getMessage()).contains("추정").contains("국민건강보험공단");
    }

    @Test
    @DisplayName("feasible이면 몬테카를로 1,000회를 돌려 성공률·백분위 잔고를 반환한다 (M16/D-169)")
    void monteCarlo_feasible_returnsStatisticallyValidResult() {
        SimulationResponseDto res = simulationService.calculate(
                request(30, 400.0, 10, 30.0, 20.0, 250.0));

        var mc = res.getMonteCarloResult();
        assertThat(res.getSummary().isFeasible()).isTrue();
        assertThat(mc).isNotNull();
        assertThat(mc.getRuns()).isEqualTo(1000);
        assertThat(mc.getSuccessRatePercent()).isBetween(0, 100);
        // 백분위는 정의상 항상 이 순서를 만족해야 한다(확률 표본이라도 정렬 후 뽑으므로 매번 성립)
        assertThat(mc.getP10EndingBalance())
                .isLessThanOrEqualTo(mc.getP50EndingBalance());
        assertThat(mc.getP50EndingBalance())
                .isLessThanOrEqualTo(mc.getP90EndingBalance());
        assertThat(mc.getP10EndingBalance()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("infeasible이면 몬테카를로를 돌리지 않는다 (M16/D-169)")
    void monteCarlo_infeasible_isNull() {
        SimulationResponseDto res = simulationService.calculate(
                request(28, 300.0, 5, 0.0, 0.0, 500.0));

        assertThat(res.getSummary().isFeasible()).isFalse();
        assertThat(res.getMonteCarloResult()).isNull();
    }

    @Test
    @DisplayName("국민연금 납입기간이 나이 대비 과도하면 IllegalArgumentException")
    void pensionYearsPaid_exceedsAgeLimit_throws() {
        // WHY: currentAge=25면 만 18세부터 최대 7년 납입 가능한데 20년을 넣음(모순 입력, 검토 Q-1)
        SimulationRequestDto req = request(25, 300.0, 20, 10.0, 10.0, 200.0);

        assertThatThrownBy(() -> simulationService.calculate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("납입 기간");
    }
}
