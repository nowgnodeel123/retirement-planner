package com.nowgnodeel.retirement_planner.common.exception;

// 이미 소진(rotate)된 refresh token이 다시 제시됨 — 탈취 의심 신호.
// InvalidRefreshTokenException과 사용자 응답은 동일하게 두되(내부 정보 노출 방지),
// 서버 로그에서는 별도 타입으로 구분해 보안 모니터링에 활용한다.
public class RefreshTokenReuseDetectedException extends RuntimeException {
    public RefreshTokenReuseDetectedException() {
        super("세션이 만료되었습니다. 다시 로그인해주세요.");
    }
}
