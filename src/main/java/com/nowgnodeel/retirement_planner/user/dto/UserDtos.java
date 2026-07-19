package com.nowgnodeel.retirement_planner.user.dto;

import com.nowgnodeel.retirement_planner.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserDtos {

    public record UpdateNicknameRequest(
            @NotBlank @Size(max = 20) String nickname
    ) {}

    public record MeResponse(
            Long id,
            String email,
            String nickname,
            String provider
    ) {
        public static MeResponse from(User user) {
            return new MeResponse(
                    user.getId(), user.getEmail(), user.getNickname(), user.getProvider().name()
            );
        }
    }
}