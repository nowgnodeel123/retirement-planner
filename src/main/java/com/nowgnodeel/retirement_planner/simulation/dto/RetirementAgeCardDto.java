package com.nowgnodeel.retirement_planner.simulation.dto;

/**
 * D-219: 포트폴리오 메인의 "은퇴 가능 나이" 카드 응답.
 *
 * 결과는 저장하지 않고 매번 계산한다(D-050) — 주식이 오르면 다음 조회에서 나이가 바뀐다.
 */
public record RetirementAgeCardDto(

        /**
         * 시뮬레이터를 한 번도 돌린 적 없으면 false. 이때 나머지 필드는 전부 null이고
         * 프론트는 카드 대신 "시뮬레이터 돌려보기" 안내를 띄운다.
         * WHY 기본값으로 대충 계산하지 않는가: 월소득·목표생활비는 사용자만 아는 값이라
         * 임의로 넣으면 그럴듯하지만 틀린 나이를 보여주게 된다.
         */
        boolean hasProfile,

        Integer estimatedRetirementAge,

        /** 75세까지도 목표를 못 채우는 경우 false — 프론트가 축하 대신 경고 톤으로 그린다. */
        Boolean feasible,

        Integer targetMonthlyExpense,

        /** 프리필과 동일한 기준의 제외 안내(시세 미조회 자산 수). 0이면 표시하지 않는다. */
        int excludedCount
) {
    public static RetirementAgeCardDto empty() {
        return new RetirementAgeCardDto(false, null, null, null, 0);
    }
}
