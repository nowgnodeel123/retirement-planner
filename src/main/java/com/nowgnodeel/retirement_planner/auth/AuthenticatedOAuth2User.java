package com.nowgnodeel.retirement_planner.auth;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class AuthenticatedOAuth2User implements OAuth2User {

    private final Long userId;
    private final Map<String, Object> attributes;

    public AuthenticatedOAuth2User(Long userId, Map<String, Object> attributes) {
        this.userId = userId;
        this.attributes = attributes;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
