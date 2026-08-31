package com.nowgnodeel.retirement_planner.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-220: 미인증 요청의 응답 형태를 고정한다.
 *
 * WHY 이 테스트가 필요한가: oauth2Login이 켜져 있으면 스프링은 미인증 요청을 전부
 * "브라우저 사용자"로 보고 카카오 인가 URL로 302 리다이렉트한다. /api/**까지 그렇게 되면
 * 프론트의 fetch가 그 302를 따라가다 CORS에 막혀 상태코드조차 없는 TypeError로 끝나고,
 * lib/api.ts의 `res.status === 401` 분기(= RTR 무음 갱신)가 영영 실행되지 않는다.
 * 실제로 그 상태로 배포 직전까지 와 있었고, 액세스 토큰 15분이 지나면 화면이 깨졌다.
 *
 * 이건 화면을 봐도 "그냥 안 되는" 것처럼만 보여서 눈으로는 잘 안 잡힌다. 그래서 두 경로의
 * 응답 형태를 테스트로 못 박는다 — API는 401 JSON, 브라우저 진입 경로는 302 리다이렉트.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityEntryPointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 API 요청은 카카오로 리다이렉트하지 않고 401 JSON을 준다")
    void apiReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("은퇴 시뮬레이터 API도 같은 규칙을 따른다(과거 302를 주던 경로)")
    void simulationApiReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/v1/simulation/retirement-age"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("permitAll인 인증 경로는 401로 막히지 않는다")
    void authEndpointsStayOpen() throws Exception {
        // 본문 없이 호출하므로 성공하진 않지만, 401(인증 요구)로 끊기면 안 된다.
        mockMvc.perform(get("/api/auth/refresh"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401) {
                        throw new AssertionError("permitAll 경로가 401로 막혔다: " + s);
                    }
                });
    }

    @Test
    @DisplayName("브라우저 OAuth 진입 경로는 그대로 리다이렉트를 유지한다")
    void oauthEntryStillRedirects() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"));
    }
}
