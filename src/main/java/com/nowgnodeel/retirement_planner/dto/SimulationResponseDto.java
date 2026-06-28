package com.nowgnodeel.retirement_planner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimulationResponseDto {

    private Summary summary;
    private Breakdown breakdown;
    private Meta meta;

    @Getter
    @Builder
    public static class Summary {
        private long totalMonthlyIncome;
        private long targetMonthlyExpense;
        private long monthlyShortfall;
        private int estimatedRetirementAge;
        private String message;
        private String shareMessage;
    }

    @Getter
    @Builder
    public static class Breakdown {
        private long nationalPension;
        private long retirementPension;
        private long irp;
    }

    @Getter
    @Builder
    public static class Meta {
        private int yearsUntilRetirement;
        private int totalPensionYears;
    }
}