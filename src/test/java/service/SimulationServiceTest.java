package service;

import com.nowgnodeel.retirement_planner.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.dto.SimulationResponseDto;
import com.nowgnodeel.retirement_planner.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService();
        ReflectionTestUtils.setField(simulationService, "aValue", 319.35);
        ReflectionTestUtils.setField(simulationService, "shareUrl", "https://retirement-planner.vercel.app");
    }

    private SimulationRequestDto createRequest(int currentAge, int retirementAge,
                                               double monthlyIncome, int pensionYearsPaid,
                                               double monthlyIrp, double targetExpense) {
        SimulationRequestDto req = new SimulationRequestDto();
        ReflectionTestUtils.setField(req, "currentAge", currentAge);
        ReflectionTestUtils.setField(req, "retirementAge", retirementAge);
        ReflectionTestUtils.setField(req, "monthlyIncome", monthlyIncome);
        ReflectionTestUtils.setField(req, "pensionYearsPaid", pensionYearsPaid);
        ReflectionTestUtils.setField(req, "monthlyIrpContribution", monthlyIrp);
        ReflectionTestUtils.setField(req, "targetMonthlyExpense", targetExpense);
        ReflectionTestUtils.setField(req, "irpReturnRate", 0.05);
        ReflectionTestUtils.setField(req, "pensionReturnRate", 0.04);
        return req;
    }

    @Test
    @DisplayName("기본 입력값으로 계산 시 국민연금 118만원 반환")
    void calculate_nationalPension_returns118() {
        SimulationRequestDto req = createRequest(28, 60, 300, 6, 30, 300);

        SimulationResponseDto result = simulationService.calculate(req);

        assertThat(result.getBreakdown().getNationalPension()).isEqualTo(118);
    }

    @Test
    @DisplayName("국민연금 가입기간 10년 미만이면 0원 반환")
    void calculate_nationalPension_lessThan10Years_returns0() {
        SimulationRequestDto req = createRequest(28, 32, 300, 0, 30, 300);

        SimulationResponseDto result = simulationService.calculate(req);

        assertThat(result.getBreakdown().getNationalPension()).isEqualTo(0);
    }

    @Test
    @DisplayName("은퇴 소득이 목표 초과 시 성공 메시지 반환")
    void calculate_message_whenGoalAchieved() {
        SimulationRequestDto req = createRequest(28, 65, 300, 6, 100, 200);

        SimulationResponseDto result = simulationService.calculate(req);

        assertThat(result.getSummary().getMonthlyShortfall()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSummary().getMessage()).contains("목표 달성");
    }

    @Test
    @DisplayName("은퇴 소득이 목표 미달 시 IRP 증액 메시지 반환")
    void calculate_message_whenShortfall() {
        SimulationRequestDto req = createRequest(28, 60, 300, 6, 30, 300);

        SimulationResponseDto result = simulationService.calculate(req);

        assertThat(result.getSummary().getMonthlyShortfall()).isLessThan(0);
        assertThat(result.getSummary().getMessage()).contains("IRP 납입액");
    }
}