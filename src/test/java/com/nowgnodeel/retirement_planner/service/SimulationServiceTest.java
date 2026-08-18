package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
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
        return req;
    }

    @Test
    @DisplayName("시나리오 A(30세/소득400/국민연금10년/IRP30/연금저축20/목표250) 골든값 고정")
    void scenarioA_goldenValues() {
        SimulationResponseDto res = simulationService.calculate(
                request(30, 400.0, 10, 30.0, 20.0, 250.0));

        assertThat(res.getSummary().getEstimatedRetirementAge()).isEqualTo(69);
        assertThat(res.getSummary().isFeasible()).isTrue();
        assertThat(res.getSummary().getTotalMonthlyIncome()).isEqualTo(915);
        assertThat(res.getSummary().getMonthlyShortfall()).isEqualTo(665);
        assertThat(res.getBreakdown().getNationalPension()).isEqualTo(219);
        assertThat(res.getBreakdown().getRetirementPension()).isEqualTo(696);
        assertThat(res.getMeta().getYearsUntilRetirement()).isEqualTo(39);
        assertThat(res.getIncomeTimeline()).hasSize(21);
    }

    @Test
    @DisplayName("시나리오 B(45세/소득600/국민연금20년/IRP50/연금저축40/목표400, 주식자산 0) — 검색상한(75세)에서도 infeasible")
    void scenarioB_noStockBuffer_isInfeasibleEvenWithHighLaterIncome() {
        SimulationResponseDto res = simulationService.calculate(
                request(45, 600.0, 20, 50.0, 40.0, 400.0));

        // WHY: 은퇴~퇴직연금 개시 전(구간1)을 메울 주식/ETF 잔액이 0이라, 첫해 소득만 보면
        // 목표를 넘겨도(총소득922 > 목표400) 90세까지 버티는 나이를 못 찾아 infeasible이 된다.
        // "feasible"이 첫해 흑자가 아니라 90세까지의 지속가능성을 뜻한다는 걸 고정해두는 케이스.
        assertThat(res.getSummary().getEstimatedRetirementAge()).isEqualTo(75);
        assertThat(res.getSummary().isFeasible()).isFalse();
        assertThat(res.getSummary().getTotalMonthlyIncome()).isEqualTo(922);
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
    @DisplayName("국민연금 납입기간이 나이 대비 과도하면 IllegalArgumentException")
    void pensionYearsPaid_exceedsAgeLimit_throws() {
        // WHY: currentAge=25면 만 18세부터 최대 7년 납입 가능한데 20년을 넣음(모순 입력, 검토 Q-1)
        SimulationRequestDto req = request(25, 300.0, 20, 10.0, 10.0, 200.0);

        assertThatThrownBy(() -> simulationService.calculate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("납입 기간");
    }
}
