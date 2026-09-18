package com.nowgnodeel.retirement_planner.simulation.dto;

/**
 * D-218: 은퇴 시뮬레이터 위저드 프리필. 사용자가 포트폴리오에 이미 입력한 자산을
 * 위저드에서 손으로 다시 입력하게 하지 않기 위한 "읽기 전용" 기본값이다.
 *
 * WHY 별도 엔드포인트인가(D-116 무상태 원칙과의 관계):
 * SimulationService는 지금도 앞으로도 userId를 모르는 순수 계산기로 남는다. 포트폴리오
 * 조회는 이 프리필 경로에만 있고, 계산 자체는 프론트가 보낸 요청 바디만 가지고 한다.
 * 즉 "계산은 무상태, 입력 채우기는 별개 경로"로 D-116을 폐기가 아니라 축소 적용한다.
 *
 * 단위는 전부 만원 — SimulationRequestDto의 금액 필드와 동일하게 맞춰서, 프론트가
 * 프리필값을 위저드 폼에 그대로 넣을 수 있게 한다(환산은 서버에서 한 번만).
 */
public record SimulationPrefillResponseDto(

        /** 프로필 birthDate 기준 만 나이. 생년월일 미입력이거나 20~74세 밖이면 null(프리필 안 함). */
        Integer currentAge,

        /** detailType=IRP 계좌의 평가금액 합(현금 포함 — 이 필드는 "계좌 잔액"이 맞다). */
        Long currentIrpBalance,

        /** detailType=PENSION_SAVINGS 계좌의 평가금액 합(현금 포함, 위와 같은 이유). */
        Long currentPensionSavingsBalance,

        /** detailType=NORMAL/ISA 계좌의 주식·코인 합. 현금은 제외한다(excludedCashAmount 참조). */
        Long stockAssetBalance,

        /**
         * 시세·환율 미조회로 합계에서 빠진 자산 수. 대시보드(DashboardService)와 동일한
         * 제외 규칙을 쓰되, 여기서는 절대 조용히 넘어가면 안 된다 — 자산이 실제보다 적게
         * 잡히면 은퇴 가능 나이가 늦게 나오는 "틀린 답"이 되기 때문. 프론트가 경고 배너로 노출.
         */
        int excludedCount,

        /**
         * 일반/ISA 계좌의 현금 중 stockAssetBalance에서 뺀 금액(만원).
         * WHY 제외: 이 값이 들어가는 위저드 필드는 은퇴 시점까지 기대수익률(기본 연 7%)로
         * 계속 불어나는 것으로 계산된다. 현금까지 넣으면 30년 뒤 몇 배로 부풀려진 답이 나온다.
         * 0이 아니면 프론트가 "현금 N만원은 빼고 채웠어요"라고 안내한다.
         */
        Long excludedCashAmount,

        /**
         * 지난번에 시뮬레이터를 돌렸을 때 사용자가 직접 입력한 값들(retirement_profiles).
         * 한 번도 안 돌렸으면 null.
         *
         * WHY 필요한가: 월소득·목표 생활비·연금 납입 년수처럼 포트폴리오에서 파생될 수 없는
         * 값들은 위저드를 다시 열 때마다 전부 다시 입력해야 했다. 저장은 D-219부터 이미
         * 하고 있었는데 되읽는 경로가 없어서, 저장된 값이 대시보드 카드에만 쓰이고
         * 정작 입력한 사람에게는 돌아오지 않았다.
         *
         * 잔액 3종은 여기서도 내려주되 위 prefill 값이 우선한다 — 포트폴리오에 실제 계좌가
         * 있으면 그게 최신이고, 여기 담긴 건 "앱에 등록하지 않은 계좌"의 손입력분이다
         * (V16 주석 및 RetirementProfileService.resolveBalance와 같은 규칙).
         *
         * 계산 결과는 담지 않는다 — 저장도 안 하고(D-050) 다시 계산하면 되는 값이다.
         */
        SavedProfile savedProfile
) {
    /** 수익률은 소수(0.07)로 내려간다 — 백엔드 저장 형식 그대로이고, %로 바꾸는 건 화면 몫이다. */
    public record SavedProfile(
            Double monthlyIncome,
            Double targetMonthlyExpense,
            Integer pensionYearsPaid,
            String pensionType,
            Integer yearsOfService,
            Double monthlyIrpContribution,
            Double monthlyPensionSavingsContribution,
            Double monthlyStockInvestment,
            Double irpReturnRate,
            Double pensionReturnRate,
            Double pensionSavingsReturnRate,
            Double stockReturnRate,
            Double dcCurrentBalance,
            Double irpBalanceManual,
            Double pensionSavingsBalanceManual,
            Double stockAssetBalanceManual
    ) {}
}
