package com.nowgnodeel.retirement_planner.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    // 아래 4개는 이메일 회원가입(LOCAL) 전용 프로필 필드. 카카오 유저는 null(D-116 세션에서 추가)
    @Column(length = 50)
    private String name;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(String email, String password, String nickname, AuthProvider provider, String providerId,
                 String name, LocalDate birthDate, Gender gender, String phone) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static User createLocal(String email, String encodedPassword, String nickname,
                                    String name, LocalDate birthDate, Gender gender, String phone) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .provider(AuthProvider.LOCAL)
                .name(name)
                .birthDate(birthDate)
                .gender(gender)
                .phone(phone)
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

    // 마이페이지 닉네임 수정용. syncKakaoProfile은 OAuth 동기화 전용이라 의도 구분을 위해 별도 메서드로 분리.
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
