package com.nowgnodeel.retirement_planner.asset;

import com.nowgnodeel.retirement_planner.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false, length = 20)
    private InstitutionType institutionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_type", nullable = false, length = 20)
    private AccountDetailType detailType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Account(User user, String name, InstitutionType institutionType, AccountDetailType detailType) {
        this.user = user;
        this.name = name;
        this.institutionType = institutionType;
        this.detailType = institutionType == InstitutionType.EXCHANGE
                ? AccountDetailType.NORMAL
                : (detailType != null ? detailType : AccountDetailType.NORMAL);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void rename(String name) {
        this.name = name;
    }
}
