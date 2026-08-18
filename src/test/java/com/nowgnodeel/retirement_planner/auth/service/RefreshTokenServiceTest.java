package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.auth.entity.RefreshToken;
import com.nowgnodeel.retirement_planner.auth.repository.RefreshTokenRepository;
import com.nowgnodeel.retirement_planner.common.exception.InvalidRefreshTokenException;
import com.nowgnodeel.retirement_planner.common.exception.RefreshTokenReuseDetectedException;
import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// RTR 회전/재사용감지 로직(D-161/M14) — 이 서비스가 토큰 탈취 방어의 핵심이라 별도로 검증한다.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenValidityMs", 1_209_600_000L);
        user = User.createLocal("test@nest.com", "encoded", "동원", "이동원",
                java.time.LocalDate.of(1995, 1, 1), com.nowgnodeel.retirement_planner.user.entity.Gender.MALE, "01012345678");
        setId(user, 1L);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private RefreshToken activeTokenFor(User owner) {
        RefreshToken token = RefreshToken.builder()
                .user(owner)
                .tokenHash("irrelevant-in-test")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();
        return token;
    }

    @Test
    @DisplayName("정상 토큰으로 rotate하면 used로 표시하고 새 토큰을 발급한다")
    void rotate_success() {
        RefreshToken existing = activeTokenFor(user);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(existing));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-token");

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.newRawToken()).isNotBlank();
        assertThat(existing.isAlreadyUsed()).isTrue();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("존재하지 않는 토큰이면 InvalidRefreshTokenException")
    void rotate_unknownToken() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("만료된 토큰이면 InvalidRefreshTokenException")
    void rotate_expiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant-in-test")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("이미 사용된(used) 토큰이 재제시되면 RefreshTokenReuseDetectedException을 던지고 유저의 모든 활성 토큰을 revoke한다")
    void rotate_reuseDetected_revokesAllActiveTokens() {
        RefreshToken alreadyUsed = activeTokenFor(user);
        alreadyUsed.markUsed();
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-token"))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        verify(refreshTokenRepository, times(1)).revokeAllActiveForUser(any(), any());
    }
}
