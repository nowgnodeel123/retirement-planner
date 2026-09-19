package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.InstitutionType;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.nowgnodeel.retirement_planner.asset.dto.AccountDtos.CreateRequest;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 없는 사용자로 계좌를 만들려 할 때 404를 준다.
 *
 * WHY: 예전엔 userRepository.getReferenceById(지연 프록시)로 User를 참조해서, 사용자가
 * 실제로 있는지 확인하지 않고 INSERT까지 갔다. 탈퇴한 사용자의 액세스 토큰은 최대 15분
 * 더 살아 있는데(refreshToken은 탈퇴 시 삭제되지만 accessToken은 만료까지 유효), 그동안
 * 계좌 생성을 호출하면 FK 제약에 걸려 500 "일시적인 문제가 발생했어요"가 나갔다.
 * 서버 잘못이 아닌데 error 로그에 스택트레이스까지 남는다.
 *
 * 실제로 탈퇴 직후 토큰으로 전 엔드포인트를 두드려 본 결과, 조회는 전부 빈 값이고
 * 다른 쓰기는 404, 리프레시는 401로 막혀 있었다 — 이 경로 하나만 500이었다.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceMissingUserTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("탈퇴한 사용자의 토큰으로 계좌를 만들면 500이 아니라 404다")
    void createWithDeletedUserReturnsNotFound() {
        long deletedUserId = 9999L;
        given(userRepository.findById(deletedUserId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.create(
                deletedUserId,
                new CreateRequest("좀비계좌", InstitutionType.SECURITIES, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        // FK 제약까지 가지 않고 조회 단계에서 끊겼는지 — 여기가 이 테스트의 핵심이다.
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
