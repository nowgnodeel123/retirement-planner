package com.nowgnodeel.retirement_planner.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;          // 카카오 유저는 이메일 미동의 시 null 가능

    private String password;       // LOCAL 전용, 카카오 유저는 null

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    private String providerId;     // KAKAO 전용 (카카오 회원번호)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(String email, String password, String nickname, AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static User createLocal(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    public static User createKakao(String providerId, String email, String nickname) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .provider(AuthProvider.KAKAO)
                .providerId(providerId)
                .build();
    }

    public void syncKakaoProfile(String nickname) {
        this.nickname = nickname;
    }
}
