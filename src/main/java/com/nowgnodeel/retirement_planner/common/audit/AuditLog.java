package com.nowgnodeel.retirement_planner.common.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// M14: 계좌/자산/배당 CUD 이력. User와 JPA 연관관계를 두지 않고 userId만 원시값으로
// 보존한다 — 감사 로그는 원인 레코드의 생애주기와 무관하게 그대로 남아야 하는
// append-only 성격이라, 연관관계로 묶어 라이프사이클을 엮고 싶지 않았다.
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    // 메서드 인자 전체를 JSON으로 직렬화한 값 — "무엇을 요청했는지"(delete/rename은
    // 대상 ID가 여기 포함됨)
    @Column(name = "request_snapshot", columnDefinition = "text")
    private String requestSnapshot;

    // 메서드 반환값을 JSON으로 직렬화한 값 — void(delete)는 null
    @Column(name = "result_snapshot", columnDefinition = "text")
    private String resultSnapshot;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AuditLog(Long userId, AuditAction action, String entityType,
                      String requestSnapshot, String resultSnapshot, String ipAddress) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.requestSnapshot = requestSnapshot;
        this.resultSnapshot = resultSnapshot;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
