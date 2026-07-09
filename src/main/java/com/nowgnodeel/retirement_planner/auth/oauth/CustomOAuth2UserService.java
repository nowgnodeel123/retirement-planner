package com.nowgnodeel.retirement_planner.auth.oauth;

import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String providerId = String.valueOf(oAuth2User.getAttributes().get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = kakaoAccount != null
                ? (Map<String, Object>) kakaoAccount.get("profile")
                : null;

        // 테스트 앱 스코프=account_email, 비즈 앱 승인 후 스코프=biz_account_email (지침 참고)
        String email = kakaoAccount != null
                ? (String) kakaoAccount.getOrDefault("account_email", kakaoAccount.get("biz_account_email"))
                : null;
        String nickname = profile != null ? (String) profile.get("nickname") : "네스트 사용자";

        User user = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
                .map(existing -> {
                    existing.syncKakaoProfile(nickname);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.createKakao(providerId, email, nickname)));

        return new AuthenticatedOAuth2User(user.getId(), oAuth2User.getAttributes());
    }
}
