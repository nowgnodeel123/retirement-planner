package com.nowgnodeel.retirement_planner.auth.service;

import com.nowgnodeel.retirement_planner.auth.entity.RefreshToken;
import com.nowgnodeel.retirement_planner.auth.repository.RefreshTokenRepository;
import com.nowgnodeel.retirement_planner.common.exception.InvalidRefreshTokenException;
import com.nowgnodeel.retirement_planner.common.exception.RefreshTokenReuseDetectedException;
import com.nowgnodeel.retirement_planner.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

// RTR(Refresh Token Rotation, D-161/M14): DB에는 원문이 아니라 SHA-256 해시만 저장한다.
// 매 회전마다 이전 토큰은 used로 표시하고 새 토큰을 발급 — 이미 used인 토큰이 다시
// 제시되면 탈취로 간주해 해당 유저의 모든 활성 토큰을 즉시 무효화한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityMs;

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plus(refreshTokenValidityMs, java.time.temporal.ChronoUnit.MILLIS))
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    public record RotationResult(Long userId, String newRawToken) {}

    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isAlreadyUsed()) {
            log.warn("Refresh token reuse detected for userId={}", existing.getUser().getId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUser().getId(), LocalDateTime.now());
            throw new RefreshTokenReuseDetectedException();
        }

        if (!existing.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        existing.markUsed();
        User user = existing.getUser();
        String newRawToken = issue(user);
        return new RotationResult(user.getId(), newRawToken);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, LocalDateTime.now());
    }

    // 로그아웃 전용 — 토큰이 이미 만료/소진돼 있어도 조용히 성공 처리한다(존재 여부를 노출하지 않음).
    @Transactional
    public void revokeAllForUserByRawToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> revokeAllForUser(token.getUser().getId()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
