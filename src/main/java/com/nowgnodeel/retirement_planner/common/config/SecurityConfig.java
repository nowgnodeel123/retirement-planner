package com.nowgnodeel.retirement_planner.common.config;

import com.nowgnodeel.retirement_planner.auth.oauth.CustomOAuth2UserService;
import com.nowgnodeel.retirement_planner.auth.oauth.OAuth2SuccessHandler;
import com.nowgnodeel.retirement_planner.common.security.JwtAuthenticationFilter;
import com.nowgnodeel.retirement_planner.common.security.JwtTokenProvider;
import com.nowgnodeel.retirement_planner.common.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;

    // 허용 origin은 환경별로 달라진다(로컬 localhost, 실기기 QA는 맥의 LAN IP, 배포는 Vercel 도메인).
    // 코드에 박아두면 QA·배포 때마다 소스를 고쳐야 하므로 프로퍼티로 뺀다. 쉼표로 여러 개 지정.
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().authenticated()
                )
                // WHY: oauth2Login을 켜면 스프링이 "미인증 = 브라우저 사용자"로 보고 기본
                // EntryPoint를 카카오 인가 URL로의 302 리다이렉트로 잡는다. 그런데 /api/**는
                // fetch로 호출되는 JSON API라, 브라우저가 그 302를 따라가 카카오 도메인에
                // 요청하고 CORS에 막혀 프론트에는 상태코드조차 없는 TypeError로 도착한다.
                // 그 결과 lib/api.ts의 `res.status === 401` 분기가 영영 실행되지 않아,
                // M14에서 만든 RTR 무음 갱신이 실사용에서 통째로 죽어 있었다
                // (액세스 토큰 15분이 지나면 화면이 그냥 깨짐).
                // API 경로만 401 JSON으로 끊어주면 프론트가 리프레시 토큰으로 조용히 갱신하고
                // 원래 요청을 재시도한다. 브라우저 진입 경로(/oauth2/**, /login/**)는 그대로
                // 리다이렉트를 써야 하므로 매처로 갈라둔다.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        apiAuthenticationEntryPoint(),
                        request -> request.getRequestURI().startsWith("/api/")
                ))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                )
                // M14: JwtAuthenticationFilter 다음이어야 인증된 요청이 userId 기준으로 묶인다.
                .addFilterAfter(new RateLimitFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * /api/** 미인증 응답. 프론트 lib/api.ts가 이 401을 보고 리프레시 토큰으로 갱신 후
     * 원래 요청을 1회 재시도한다. 본문 형식은 AuthExceptionHandler와 같은 {message}.
     */
    private AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"로그인이 필요해요.\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
