package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.simulation.dto.RetirementAgeCardDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.entity.RetirementProfile;
import com.nowgnodeel.retirement_planner.simulation.repository.RetirementProfileRepository;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetirementProfileServiceTest {

    @Mock RetirementProfileRepository retirementProfileRepository;
    @Mock UserRepository userRepository;
    @Mock SimulationPrefillService simulationPrefillService;
    @Mock SimulationService simulationService;
    @InjectMocks RetirementProfileService retirementProfileService;

    private static final Long USER_ID = 1L;

    private SimulationPrefillResponseDto prefill(Integer age, long irp, long pensionSavings, long stock, int excluded) {
        return new SimulationPrefillResponseDto(age, irp, pensionSavings, stock, excluded, 0L);
    }

    private RetirementProfile profile(double manualIrp, double manualStock) {
        return RetirementProfile.builder()
                .user(mock(User.class))
                .currentAge(34).ageAsOf(LocalDate.now())
                .monthlyIncome(350.0).targetMonthlyExpense(300.0)
                .pensionYearsPaid(3).pensionType("DB").yearsOfService(3)
                .nationalPensionReceiptType("NORMAL").militaryServiceMonths(0).childrenCount(0)
                .monthlyIrpContribution(25.0).monthlyPensionSavingsContribution(50.0)
                .monthlyStockInvestment(50.0)
                .irpReturnRate(0.05).pensionReturnRate(0.04)
                .pensionSavingsReturnRate(0.06).stockReturnRate(0.07)
                .dcCurrentBalanceManual(0.0).irpBalanceManual(manualIrp)
                .pensionSavingsBalanceManual(0.0).stockAssetBalanceManual(manualStock)
                .usePreciseHealthInsurance(false).realEstateValue(0.0).financialAssetValue(0.0)
                .build();
    }

    private SimulationRequestDto captureRequest() {
        ArgumentCaptor<SimulationRequestDto> captor = ArgumentCaptor.forClass(SimulationRequestDto.class);
        verify(simulationService).calculateRetirementAgeOnly(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("D-219: 시뮬레이터를 한 번도 안 돌렸으면 hasProfile=false — 기본값으로 대충 계산하지 않는다")
    void getCard_noProfile() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        RetirementAgeCardDto card = retirementProfileService.getCard(USER_ID);

        assertThat(card.hasProfile()).isFalse();
        assertThat(card.estimatedRetirementAge()).isNull();
        verify(simulationService, never()).calculateRetirementAgeOnly(any());
    }

    @Test
    @DisplayName("D-219: 포트폴리오 자산이 저장된 손입력을 이긴다 — 앱에 등록된 계좌가 단일 소스")
    void getCard_prefillWinsOverManual() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile(1000.0, 2000.0)));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(34, 3312L, 0L, 4995L, 0));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willReturn(new SimulationService.RetirementAgeOnly(63, true));

        retirementProfileService.getCard(USER_ID);

        SimulationRequestDto req = captureRequest();
        assertThat(req.getCurrentIrpBalance()).isEqualTo(3312.0);
        assertThat(req.getStockAssetBalance()).isEqualTo(4995.0);
    }

    @Test
    @DisplayName("D-219: 포트폴리오에 없는 자산(프리필 0)은 저장된 손입력으로 채운다 — 미등록 IRP가 0이 되면 은퇴 나이가 늦게 나온다")
    void getCard_fallsBackToManualWhenPrefillIsZero() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile(3000.0, 0.0)));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(34, 0L, 0L, 4995L, 0));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willReturn(new SimulationService.RetirementAgeOnly(63, true));

        retirementProfileService.getCard(USER_ID);

        SimulationRequestDto req = captureRequest();
        assertThat(req.getCurrentIrpBalance()).isEqualTo(3000.0);   // 손입력 사용
        assertThat(req.getStockAssetBalance()).isEqualTo(4995.0);   // 프리필 사용
    }

    @Test
    @DisplayName("D-219: 생년월일이 없는 계정(카카오)은 저장 시점 나이에 경과 연수를 더해 보정한다")
    void getCard_agesStoredValueWhenNoBirthDate() {
        RetirementProfile aged = profile(0.0, 0.0);
        org.springframework.test.util.ReflectionTestUtils.setField(
                aged, "ageAsOf", LocalDate.now().minusYears(2));
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(aged));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(null, 0L, 0L, 4995L, 0));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willReturn(new SimulationService.RetirementAgeOnly(63, true));

        retirementProfileService.getCard(USER_ID);

        assertThat(captureRequest().getCurrentAge()).isEqualTo(36); // 34 + 2년
    }

    @Test
    @DisplayName("D-219: 프로필 나이보다 birthDate 기반 프리필 나이가 우선한다")
    void getCard_prefillAgeWins() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile(0.0, 0.0)));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(40, 0L, 0L, 4995L, 0));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willReturn(new SimulationService.RetirementAgeOnly(63, true));

        retirementProfileService.getCard(USER_ID);

        assertThat(captureRequest().getCurrentAge()).isEqualTo(40);
    }

    @Test
    @DisplayName("D-219: 저장된 입력이 지금은 모순이어도 포트폴리오 화면을 깨뜨리지 않는다")
    void getCard_swallowsInvalidStoredInput() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile(0.0, 0.0)));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(34, 0L, 0L, 4995L, 0));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willThrow(new IllegalArgumentException("국민연금 납입 기간이 나이에 비해 너무 길어요."));

        assertThat(retirementProfileService.getCard(USER_ID).hasProfile()).isFalse();
    }

    @Test
    @DisplayName("D-219: 시세 미조회 자산 수는 카드에도 그대로 전달한다(프리필과 같은 기준)")
    void getCard_passesExcludedCount() {
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile(0.0, 0.0)));
        given(simulationPrefillService.getPrefill(USER_ID)).willReturn(prefill(34, 0L, 0L, 4995L, 2));
        given(simulationService.calculateRetirementAgeOnly(any()))
                .willReturn(new SimulationService.RetirementAgeOnly(63, true));

        RetirementAgeCardDto card = retirementProfileService.getCard(USER_ID);

        assertThat(card.excludedCount()).isEqualTo(2);
        assertThat(card.estimatedRetirementAge()).isEqualTo(63);
        assertThat(card.feasible()).isTrue();
    }

    @Test
    @DisplayName("D-219: 기존 프로필이 있으면 새로 만들지 않고 덮어쓴다(사용자당 1행)")
    void save_updatesExistingProfile() {
        RetirementProfile existing = profile(0.0, 0.0);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(mock(User.class)));
        given(retirementProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(existing));

        SimulationRequestDto req = SimulationRequestDto.builder()
                .currentAge(40).monthlyIncome(500.0).targetMonthlyExpense(400.0)
                .pensionYearsPaid(10).monthlyIrpContribution(30.0)
                .monthlyPensionSavingsContribution(60.0).build();

        retirementProfileService.save(USER_ID, req);

        verify(retirementProfileRepository, never()).save(any());
        assertThat(existing.getCurrentAge()).isEqualTo(40);
        assertThat(existing.getMonthlyIncome()).isEqualTo(500.0);
        assertThat(existing.getTargetMonthlyExpense()).isEqualTo(400.0);
    }

    @Test
    @DisplayName("D-219: 빌더는 SimulationRequestDto의 필드 기본값(수익률 5/4/6/7%)을 지운다면 은퇴 나이가 크게 늦어진다 — 유지 확인")
    void builder_preservesFieldDefaults() {
        SimulationRequestDto req = SimulationRequestDto.builder()
                .currentAge(34).monthlyIncome(350.0).pensionYearsPaid(3)
                .monthlyIrpContribution(25.0).monthlyPensionSavingsContribution(50.0)
                .targetMonthlyExpense(300.0).build();

        assertThat(req.getIrpReturnRate()).isEqualTo(0.05);
        assertThat(req.getPensionReturnRate()).isEqualTo(0.04);
        assertThat(req.getPensionSavingsReturnRate()).isEqualTo(0.06);
        assertThat(req.getStockReturnRate()).isEqualTo(0.07);
        assertThat(req.getPensionType()).isEqualTo("DC");
        assertThat(req.getNationalPensionReceiptType()).isEqualTo("NORMAL");
    }
}
