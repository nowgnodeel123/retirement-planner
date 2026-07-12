package com.nowgnodeel.retirement_planner.asset.dto;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.InstitutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class AccountDtos {

    public record CreateRequest(
            @NotBlank String name,
            @NotNull InstitutionType institutionType,
            AccountDetailType detailType   // EXCHANGE면 무시되고 엔티티가 NORMAL로 강제(Account.java 참고), null 허용
    ) {}

    public record Response(
            Long id,
            String name,
            String institutionType,
            String detailType,
            LocalDateTime createdAt
    ) {
        public static Response from(Account account) {
            return new Response(
                    account.getId(),
                    account.getName(),
                    account.getInstitutionType().name(),
                    account.getDetailType().name(),
                    account.getCreatedAt()
            );
        }
    }
}