package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.simulation.dto.RetirementAgeCardDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationRequestDto;
import com.nowgnodeel.retirement_planner.simulation.entity.RetirementProfile;
import com.nowgnodeel.retirement_planner.simulation.repository.RetirementProfileRepository;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * D-219: 시뮬레이터 입력값 저장 + 포트폴리오 대시보드 카드 계산.
 *
 * SimulationService(순수 계산기)와 SimulationPrefillService(포트폴리오 읽기)를 조립만 하고,
 * 자기 자신은 계산 로직을 갖지 않는다. 카드와 위저드가 다른 답을 내는 일이 없도록
 * 은퇴 나이는 반드시 SimulationService를 거친다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetirementProfileService {

    private final RetirementProfileRepository retirementProfileRepository;
    private final UserRepository userRepository;
    private final SimulationPrefillService simulationPrefillService;
    private final SimulationService simulationService;

    /**
     * 시뮬레이션을 돌릴 때마다 그 입력을 사용자 프로필로 저장(사용자당 1행, 덮어쓰기).
     * 별도 저장 버튼을 두지 않는 이유: 카드가 뜨려면 입력이 있어야 하는데, 저장을 따로
     * 시키면 대부분 안 누르고 카드는 영영 안 뜬다.
     */
    @Transactional
    public void save(Long userId, SimulationRequestDto req) {
        // 빈 orElseThrow()는 NoSuchElementException을 던져 catch-all에 걸려 500이 된다.
        // 사용자가 없는 건 서버 오류가 아니라 404다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        RetirementProfile incoming = toEntity(user, req);

        // 소유자 검증: userId로만 조회하므로 남의 프로필을 덮어쓸 경로가 없다.
        retirementProfileRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.update(incoming),
                        () -> retirementProfileRepository.save(incoming));
    }

    /**
     * 대시보드 카드. 저장된 손입력 + 지금 포트폴리오 자산으로 매번 다시 계산한다.
     *
     * 자산은 프리필이 우선하고, 프리필이 0인 항목만 저장된 손입력으로 채운다 — 위저드의
     * applyPrefill과 정확히 같은 규칙이라, 카드는 항상 "지금 위저드를 열어 그대로 제출했을
     * 때의 답"과 일치한다.
     */
    @Transactional(readOnly = true)
    public RetirementAgeCardDto getCard(Long userId) {
        Optional<RetirementProfile> found = retirementProfileRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return RetirementAgeCardDto.empty();
        }
        RetirementProfile profile = found.get();
        SimulationPrefillResponseDto prefill = simulationPrefillService.getPrefill(userId);

        SimulationRequestDto req = toRequest(profile, prefill);
        try {
            SimulationService.RetirementAgeOnly result = simulationService.calculateRetirementAgeOnly(req);
            return new RetirementAgeCardDto(
                    true,
                    result.estimatedRetirementAge(),
                    result.feasible(),
                    (int) Math.round(profile.getTargetMonthlyExpense()),
                    prefill.excludedCount());
        } catch (IllegalArgumentException e) {
            // 저장 당시엔 유효했지만 지금은 모순이 된 입력(예: 나이 보정 결과). 카드를 못 그릴 뿐
            // 포트폴리오 화면 전체가 깨지면 안 되므로 "프로필 없음"과 같게 취급한다.
            log.warn("은퇴 카드 계산 실패 — userId={}, reason={}", userId, e.getMessage());
            return RetirementAgeCardDto.empty();
        }
    }

    /** 프리필 값이 있으면 그것을, 없으면(0) 저장된 손입력을 쓴다. */
    private double resolveBalance(Long prefilled, Double manual) {
        if (prefilled != null && prefilled > 0) {
            return prefilled.doubleValue();
        }
        return manual != null ? manual : 0.0;
    }

    private SimulationRequestDto toRequest(RetirementProfile p, SimulationPrefillResponseDto prefill) {
        // 나이는 프로필 birthDate 기반 프리필이 있으면 그것이 정확하다(카카오 계정은 null).
        // 없으면 저장 시점 나이에 경과 연수를 더해 보정한다.
        int age = prefill.currentAge() != null ? prefill.currentAge() : p.ageToday(LocalDate.now());

        return SimulationRequestDto.builder()
                .currentAge(age)
                .monthlyIncome(p.getMonthlyIncome())
                .targetMonthlyExpense(p.getTargetMonthlyExpense())
                .pensionYearsPaid(p.getPensionYearsPaid())
                .pensionType(p.getPensionType())
                .yearsOfService(p.getYearsOfService())
                .nationalPensionReceiptType(p.getNationalPensionReceiptType())
                .nationalPensionReceiptAge(p.getNationalPensionReceiptAge())
                .militaryServiceMonths(p.getMilitaryServiceMonths())
                .childrenCount(p.getChildrenCount())
                .monthlyIrpContribution(p.getMonthlyIrpContribution())
                .monthlyPensionSavingsContribution(p.getMonthlyPensionSavingsContribution())
                .monthlyStockInvestment(p.getMonthlyStockInvestment())
                .irpReturnRate(p.getIrpReturnRate())
                .pensionReturnRate(p.getPensionReturnRate())
                .pensionSavingsReturnRate(p.getPensionSavingsReturnRate())
                .stockReturnRate(p.getStockReturnRate())
                // DC는 포트폴리오에 대응 계좌 유형이 없어 프리필 소스가 없다 — 항상 손입력.
                .dcCurrentBalance(p.getDcCurrentBalanceManual())
                .currentIrpBalance(resolveBalance(prefill.currentIrpBalance(), p.getIrpBalanceManual()))
                .currentPensionSavingsBalance(
                        resolveBalance(prefill.currentPensionSavingsBalance(), p.getPensionSavingsBalanceManual()))
                .stockAssetBalance(resolveBalance(prefill.stockAssetBalance(), p.getStockAssetBalanceManual()))
                .usePreciseHealthInsurance(p.isUsePreciseHealthInsurance())
                .realEstateValue(p.getRealEstateValue())
                .financialAssetValue(p.getFinancialAssetValue())
                .build();
    }

    private RetirementProfile toEntity(User user, SimulationRequestDto req) {
        return RetirementProfile.builder()
                .user(user)
                .currentAge(req.getCurrentAge())
                .ageAsOf(LocalDate.now())
                .monthlyIncome(req.getMonthlyIncome())
                .targetMonthlyExpense(req.getTargetMonthlyExpense())
                .pensionYearsPaid(req.getPensionYearsPaid())
                .pensionType(req.getPensionType())
                .yearsOfService(req.getYearsOfService())
                .nationalPensionReceiptType(req.getNationalPensionReceiptType())
                .nationalPensionReceiptAge(req.getNationalPensionReceiptAge())
                .militaryServiceMonths(req.getMilitaryServiceMonths())
                .childrenCount(req.getChildrenCount())
                .monthlyIrpContribution(req.getMonthlyIrpContribution())
                .monthlyPensionSavingsContribution(req.getMonthlyPensionSavingsContribution())
                .monthlyStockInvestment(req.getMonthlyStockInvestment())
                .irpReturnRate(req.getIrpReturnRate())
                .pensionReturnRate(req.getPensionReturnRate())
                .pensionSavingsReturnRate(req.getPensionSavingsReturnRate())
                .stockReturnRate(req.getStockReturnRate())
                .dcCurrentBalanceManual(req.getDcCurrentBalance())
                .irpBalanceManual(req.getCurrentIrpBalance())
                .pensionSavingsBalanceManual(req.getCurrentPensionSavingsBalance())
                .stockAssetBalanceManual(req.getStockAssetBalance())
                .usePreciseHealthInsurance(req.isUsePreciseHealthInsurance())
                .realEstateValue(req.getRealEstateValue())
                .financialAssetValue(req.getFinancialAssetValue())
                .build();
    }
}
