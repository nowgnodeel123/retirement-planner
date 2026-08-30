package com.nowgnodeel.retirement_planner.service;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.asset.service.AssetService;
import com.nowgnodeel.retirement_planner.dto.SimulationPrefillResponseDto;
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
                toManWon(excludedCash)
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
