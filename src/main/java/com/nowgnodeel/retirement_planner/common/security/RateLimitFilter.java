package com.nowgnodeel.retirement_planner.common.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// M14: 시세·환율·종목검색 API(분당 60회)와 자산/계좌 등록·수정 API(분당 30회)에
// 사용자(로그인 시 userId, 아니면 IP)별 호출 제한을 건다. Redis 없이 인메모리로
// 충분한 규모(단일 인스턴스)라 Bucket4j core만 사용. JwtAuthenticationFilter
// 다음에 실행되어야 인증된 요청은 userId 기준으로 묶인다.
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> READ_LIMITED_PATTERNS = List.of(
            "/api/domestic-stocks/**",
            "/api/foreign-stocks/**",
            "/api/exchange-rates/**",
            "/api/assets/**",
            "/api/portfolio/**"
    );

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> WRITE_LIMITED_PATTERNS = List.of(
            "/api/accounts/**",
            "/api/assets/**"
    );

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RateLimitCategory category = classify(request);
        if (category == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = resolveIdentity(request);
        Bucket bucket = buckets.computeIfAbsent(category.name() + ":" + identity, k -> newBucket(category));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"message\":\"요청이 너무 많아요. 잠시 후 다시 시도해주세요.\"}");
    }

    private enum RateLimitCategory {
        READ(60), WRITE(30);

        final int perMinute;

        RateLimitCategory(int perMinute) {
            this.perMinute = perMinute;
        }
    }

    private RateLimitCategory classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isWrite = WRITE_METHODS.contains(request.getMethod())
                && WRITE_LIMITED_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
        if (isWrite) {
            return RateLimitCategory.WRITE;
        }
        boolean isRead = "GET".equals(request.getMethod())
                && READ_LIMITED_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
        return isRead ? RateLimitCategory.READ : null;
    }

    private String resolveIdentity(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return "user:" + userId;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket newBucket(RateLimitCategory category) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(category.perMinute)
                .refillGreedy(category.perMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
