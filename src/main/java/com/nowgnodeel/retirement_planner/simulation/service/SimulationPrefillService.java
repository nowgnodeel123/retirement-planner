package com.nowgnodeel.retirement_planner.simulation.service;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import com.nowgnodeel.retirement_planner.simulation.dto.SimulationPrefillResponseDto;
import com.nowgnodeel.retirement_planner.simulation.entity.RetirementProfile;
import com.nowgnodeel.retirement_planner.simulation.repository.RetirementProfileRepository;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.HoldingResponse;

/**
 * D-218: 포트폴리오 → 은퇴 시뮬레이터 위저드 프리필(읽기 전용).
 *
 * SimulationService는 건드리지 않는다 — 계산기는 계속 완전 무상태이고, 사용자 데이터를
 * 읽는 책임은 이 클래스 하나에만 둔다. 되돌려야 할 때 이 파일과 컨트롤러 메서드 하나만
 * 지우면 원래 구조로 정확히 돌아온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationPrefillService {

    private static final BigDecimal MAN_WON = BigDecimal.valueOf(10_000);

    // SimulationRequestDto의 @Min(20)/@Max(74)와 같은 범위. 이 밖의 나이를 프리필하면
    // 사용자가 손대지 않은 값 때문에 제출이 400으로 튕긴다 — 그럴 바엔 비워두는 게 낫다.
    private static final int MIN_AGE = 20;
    private static final int MAX_AGE = 74;

    private final AssetService assetService;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    // 리포지토리를 직접 잡는다 — RetirementProfileService는 이 클래스를 이미 의존하고 있어서
    // 서비스끼리 부르면 순환 의존이 된다.
    private final RetirementProfileRepository retirementProfileRepository;

    public SimulationPrefillResponseDto getPrefill(Long userId) {
        // 소유자 검증: 계좌·보유자산 모두 userId로 스코프된 조회만 사용한다(원칙 — 서비스 계층에서 강제).
        Map<Long, AccountDetailType> detailTypeByAccountId = new HashMap<>();
        for (Account account : accountRepository.findAllByUserId(userId)) {
            detailTypeByAccountId.put(account.getId(), account.getDetailType());
        }

        BigDecimal irp = BigDecimal.ZERO;
        BigDecimal pensionSavings = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        BigDecimal excludedCash = BigDecimal.ZERO;
        int excludedCount = 0;

        for (HoldingResponse holding : assetService.findAllHoldingsByUser(userId)) {
            // 전량매도(보유수량 0)는 제외 대상이 아니라 셈에서 빠지는 게 정상이다.
            // 여기서 제외로 세면 경고 배너가 "자산이 덜 잡혔다"고 거짓말을 한다
            // — 이 배너는 답이 과소평가됐다는 신호라 거짓 경보의 비용이 특히 크다.
            if (holding.quantity() != null && holding.quantity().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal evalKrw = toKrwEvaluation(holding);
            if (evalKrw == null) {
                excludedCount++;
                continue;
            }

            AccountDetailType detailType = detailTypeByAccountId.get(holding.accountId());
            if (detailType == null) {
                // 계좌를 못 찾는 건 정상 경로에 없다. 조용히 0으로 흡수하지 말고 제외로 세서
                // 합계가 실제보다 작다는 사실이 배너에 드러나게 한다.
                excludedCount++;
                continue;
            }

            switch (detailType) {
                case IRP -> irp = irp.add(evalKrw);
                case PENSION_SAVINGS -> pensionSavings = pensionSavings.add(evalKrw);
                case NORMAL, ISA -> {
                    if (AssetCategory.CASH.name().equals(holding.category())) {
                        excludedCash = excludedCash.add(evalKrw);
                    } else {
                        stock = stock.add(evalKrw);
                    }
                }
            }
        }

        return new SimulationPrefillResponseDto(
                resolveCurrentAge(userId),
                toManWon(irp),
                toManWon(pensionSavings),
                toManWon(stock),
                excludedCount,
                toManWon(excludedCash),
                resolveSavedProfile(userId)
        );
    }

    /**
     * 지난번 시뮬레이션 입력. 소유자 검증은 userId 스코프 조회 하나로 강제한다.
     * 한 번도 안 돌렸으면 null — 프론트가 "없음"과 "0"을 구분할 수 있어야 한다.
     */
    private SimulationPrefillResponseDto.SavedProfile resolveSavedProfile(Long userId) {
        return retirementProfileRepository.findByUserId(userId)
                .map(this::toSavedProfile)
                .orElse(null);
    }

    private SimulationPrefillResponseDto.SavedProfile toSavedProfile(RetirementProfile p) {
        return new SimulationPrefillResponseDto.SavedProfile(
                p.getMonthlyIncome(),
                p.getTargetMonthlyExpense(),
                p.getPensionYearsPaid(),
                p.getPensionType(),
                p.getYearsOfService(),
                p.getMonthlyIrpContribution(),
                p.getMonthlyPensionSavingsContribution(),
                p.getMonthlyStockInvestment(),
                p.getIrpReturnRate(),
                p.getPensionReturnRate(),
                p.getPensionSavingsReturnRate(),
                p.getStockReturnRate(),
                p.getDcCurrentBalanceManual(),
                p.getIrpBalanceManual(),
                p.getPensionSavingsBalanceManual(),
                p.getStockAssetBalanceManual()
        );
    }

    /**
     * DashboardService.getSummary()와 정확히 같은 제외 규칙 — 원화환산 가능 여부는 카테고리가
     * 아니라 통화로 판단하고, 환산에 필요한 값이 없으면 합계에서 뺀다(null 반환 = 제외).
     * 두 곳의 규칙이 갈라지면 대시보드 총자산과 프리필 금액이 서로 안 맞아 더 혼란스러워진다.
     */
    private BigDecimal toKrwEvaluation(HoldingResponse holding) {
        if (!"KRW".equals(holding.currency())) {
            return holding.krwEvaluationAmount() != null && holding.exchangeRate() != null
                    ? holding.krwEvaluationAmount()
                    : null;
        }
        return holding.evaluationAmount();
    }

    /** 생년월일이 있고 시뮬레이터가 받는 범위 안일 때만 만 나이를 채운다. */
    private Integer resolveCurrentAge(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getBirthDate())
                .filter(Objects::nonNull)
                .map(birthDate -> Period.between(birthDate, LocalDate.now()).getYears())
                .filter(age -> age >= MIN_AGE && age <= MAX_AGE)
                .orElse(null);
    }

    private Long toManWon(BigDecimal krw) {
        return krw.divide(MAN_WON, 0, RoundingMode.HALF_UP).longValue();
    }
}
