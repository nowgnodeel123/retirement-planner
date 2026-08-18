package com.nowgnodeel.retirement_planner.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

// M14: 시세 조회(READ, 분당 60회)/자산 등록(WRITE, 분당 30회) 한도가 실제로
// 걸리는지, 한도에 안 걸리는 경로는 그대로 통과하는지 확인한다.
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("한도 범위 내 요청은 계속 통과한다")
    void withinLimit_passesThrough() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest req = getRequest("/api/domestic-stocks/search");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200); // MockHttpServletResponse 기본값
        }
        Mockito.verify(chain, Mockito.times(30)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("READ 한도(분당 60회)를 초과하면 429를 반환한다")
    void exceedsReadLimit_returns429() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletResponse last = null;

        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest req = getRequest("/api/foreign-stocks/search");
            last = new MockHttpServletResponse();
            filter.doFilter(req, last, chain);
        }

        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getHeader("Retry-After")).isEqualTo("60");
        Mockito.verify(chain, Mockito.times(60)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("WRITE 한도(분당 30회)는 READ보다 더 낮게 잡혀 먼저 막힌다")
    void writeLimit_isStricterThanRead() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletResponse last = null;

        for (int i = 0; i < 31; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/assets/buy");
            req.setRemoteAddr("10.0.0.5");
            last = new MockHttpServletResponse();
            filter.doFilter(req, last, chain);
        }

        assertThat(last.getStatus()).isEqualTo(429);
        Mockito.verify(chain, Mockito.times(30)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("한도 대상이 아닌 경로(예: /api/auth/login)는 무제한 통과한다")
    void unrelatedPath_isNeverLimited() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.9");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }
        Mockito.verify(chain, Mockito.times(100)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("로그인 사용자는 IP가 아니라 userId 기준으로 한도가 묶인다")
    void authenticatedUser_isLimitedByUserIdNotIp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, Collections.emptyList()));

        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletResponse last = null;
        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/portfolio/summary");
            req.setRemoteAddr("1.2.3." + (i % 5)); // IP가 바뀌어도 userId로 묶여야 함
            last = new MockHttpServletResponse();
            filter.doFilter(req, last, chain);
        }

        assertThat(last.getStatus()).isEqualTo(429);
        Mockito.verify(chain, Mockito.times(60)).doFilter(Mockito.any(), Mockito.any());
    }

    private MockHttpServletRequest getRequest(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }
}
