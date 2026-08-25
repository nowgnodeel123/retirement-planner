package com.nowgnodeel.retirement_planner.auth.repository;

import com.nowgnodeel.retirement_planner.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now " +
            "where r.user.id = :userId and r.revokedAt is null and r.usedAt is null")
    void revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 회원탈퇴 전용 — users.id를 참조하는 FK라 user row 삭제 전에 반드시 먼저 지워야 한다.
    void deleteAllByUserId(Long userId);
}
