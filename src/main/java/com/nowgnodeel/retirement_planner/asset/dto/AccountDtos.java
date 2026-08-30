package com.nowgnodeel.retirement_planner.asset.dto;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.entity.AccountDetailType;
import com.nowgnodeel.retirement_planner.asset.entity.InstitutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class AccountDtos {

    public record CreateRequest(
            @NotBlank String name,
            @NotNull InstitutionType institutionType,
            AccountDetailType detailType   // EXCHANGE면 무시되고 엔티티가 NORMAL로 강제(Account.java 참고), null 허용
    ) {}

    public record RenameRequest(
            @NotBlank String name
    ) {}

    /** 사용자가 끌어서 정한 순서. 화면에 보이는 순서 그대로의 id 목록을 받는다. */
    public record ReorderRequest(
            @NotEmpty List<Long> orderedIds
    ) {}

    public record Response(
            Long id,
            String name,
            String institutionType,
            String detailType,
            Integer sortOrder,
            LocalDateTime createdAt
    ) {
        public static Response from(Account account) {
            return new Response(
                    account.getId(),
                    account.getName(),
                    account.getInstitutionType().name(),
                    account.getDetailType().name(),
                    account.getSortOrder(),
                    account.getCreatedAt()
            );
        }
    }
}