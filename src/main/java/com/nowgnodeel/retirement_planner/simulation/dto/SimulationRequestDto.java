package com.nowgnodeel.retirement_planner.simulation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SimulationRequestDto {

    // WHY @Max(74): 최소 은퇴나이 탐색이 currentAge+1 ~ 75세 범위라,
    // 75세 이상 입력 시 탐색 루프가 한 번도 돌지 않아 NPE가 발생한다. (검토 A-1)
    @NotNull @Min(20) @Max(74)
    private Integer currentAge;

    @NotNull @Min(0)
    private Double monthlyIncome;

    @NotNull @Min(0)
    private Integer pensionYearsPaid;

    private String pensionType = "DC";

    // DB형 전용: 입사 후 지금까지 이미 쌓인 근속연수.
    // WHY: 퇴직금 = 최종월급 × (과거 근속 + 미래 근속)이므로 과거분이 빠지면 과소 계산된다.
    @Min(0)
    private Integer yearsOfService = 0;

    // DC형 전용: 현재까지 적립된 퇴직연금 잔액(만원).
    // WHY: 기존 잔액도 은퇴 시점까지 수익률로 계속 굴러가므로 계산에 반드시 포함해야 한다.
    @Min(0)
    private Double dcCurrentBalance = 0.0;

    private String nationalPensionReceiptType = "NORMAL";
    private Integer nationalPensionReceiptAge;

    private Integer militaryServiceMonths = 0;
    private Integer childrenCount = 0;

    @NotNull @Min(0)
    private Double monthlyIrpContribution;

    private Double currentIrpBalance = 0.0;

    @NotNull @Min(0)
    private Double monthlyPensionSavingsContribution;

    private Double currentPensionSavingsBalance = 0.0;

    @NotNull @Min(0)
    private Double targetMonthlyExpense;

    // WHY 수익률 상한(연 100%): 프론트는 자릿수 캡으로 막지만 API 직접 호출은
    // 못 막는다. 비현실적 수익률로 오버플로우에 가까운 결과가 나오는 것을 방지. (검토 권고)
    //
    // 기본값은 화면 placeholder와 반드시 같아야 한다(IRP 6 / DC 4 / 연금저축 8 / 주식 10).
    // 프론트 types.ts의 toDecimalRate defaultPercent와 한 쌍이다 — 한쪽만 고치면
    // "화면이 말하는 값"과 "실제 계산에 들어가는 값"이 갈린다.
    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double irpReturnRate = 0.06;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double pensionReturnRate = 0.04;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double pensionSavingsReturnRate = 0.08;

    private Double stockAssetBalance = 0.0;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double stockReturnRate = 0.10;

    private Double monthlyStockInvestment = 0.0;

    private boolean usePreciseHealthInsurance = false;
    private Double realEstateValue = 0.0;
    private Double financialAssetValue = 0.0;

    // ==================================================================
    // D-219: 대시보드 카드가 저장된 프로필로 이 요청을 조립하기 위한 빌더.
    //
    // WHY Lombok @Builder를 쓰지 않는가: @Builder는 전체 필드 생성자를 만들면서
    // 암묵적 기본 생성자를 없앤다. 그러면 Jackson이 /calculate 요청 바디를 역직렬화하지
    // 못해 기존 엔드포인트가 통째로 깨진다. 또 필드 초기화값(0.05/0.04/0.06/0.07 등)도
    // @Builder.Default 없이는 조용히 무시된다 — 수익률이 0으로 들어가면 은퇴 나이가
    // 실제보다 훨씬 늦게 나오는데 예외 하나 없이 그냥 틀린 답이 된다.
    // 같은 클래스 안의 수제 빌더는 새 인스턴스의 필드를 직접 채우므로 생성자도
    // 기본값도 건드리지 않는다.
    // ==================================================================
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SimulationRequestDto dto = new SimulationRequestDto();

        public Builder currentAge(Integer v) { dto.currentAge = v; return this; }
        public Builder monthlyIncome(Double v) { dto.monthlyIncome = v; return this; }
        public Builder pensionYearsPaid(Integer v) { dto.pensionYearsPaid = v; return this; }
        public Builder pensionType(String v) { if (v != null) dto.pensionType = v; return this; }
        public Builder yearsOfService(Integer v) { if (v != null) dto.yearsOfService = v; return this; }
        public Builder dcCurrentBalance(Double v) { if (v != null) dto.dcCurrentBalance = v; return this; }
        public Builder nationalPensionReceiptType(String v) { if (v != null) dto.nationalPensionReceiptType = v; return this; }
        public Builder nationalPensionReceiptAge(Integer v) { dto.nationalPensionReceiptAge = v; return this; }
        public Builder militaryServiceMonths(Integer v) { if (v != null) dto.militaryServiceMonths = v; return this; }
        public Builder childrenCount(Integer v) { if (v != null) dto.childrenCount = v; return this; }
        public Builder monthlyIrpContribution(Double v) { dto.monthlyIrpContribution = v; return this; }
        public Builder currentIrpBalance(Double v) { if (v != null) dto.currentIrpBalance = v; return this; }
        public Builder monthlyPensionSavingsContribution(Double v) { dto.monthlyPensionSavingsContribution = v; return this; }
        public Builder currentPensionSavingsBalance(Double v) { if (v != null) dto.currentPensionSavingsBalance = v; return this; }
        public Builder targetMonthlyExpense(Double v) { dto.targetMonthlyExpense = v; return this; }
        public Builder irpReturnRate(Double v) { if (v != null) dto.irpReturnRate = v; return this; }
        public Builder pensionReturnRate(Double v) { if (v != null) dto.pensionReturnRate = v; return this; }
        public Builder pensionSavingsReturnRate(Double v) { if (v != null) dto.pensionSavingsReturnRate = v; return this; }
        public Builder stockAssetBalance(Double v) { if (v != null) dto.stockAssetBalance = v; return this; }
        public Builder stockReturnRate(Double v) { if (v != null) dto.stockReturnRate = v; return this; }
        public Builder monthlyStockInvestment(Double v) { if (v != null) dto.monthlyStockInvestment = v; return this; }
        public Builder usePreciseHealthInsurance(boolean v) { dto.usePreciseHealthInsurance = v; return this; }
        public Builder realEstateValue(Double v) { if (v != null) dto.realEstateValue = v; return this; }
        public Builder financialAssetValue(Double v) { if (v != null) dto.financialAssetValue = v; return this; }

        public SimulationRequestDto build() { return dto; }
    }
}
