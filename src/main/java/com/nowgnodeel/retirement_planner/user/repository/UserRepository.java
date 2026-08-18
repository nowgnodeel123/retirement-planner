package com.nowgnodeel.retirement_planner.user.repository;

import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // M14: phone 컬럼은 랜덤 IV로 암호화되어 있어 동등 조회가 불가능하다.
    // phoneHash(결정적 HMAC)로만 조회한다.
    boolean existsByPhoneHash(String phoneHash);

    Optional<User> findByPhoneHash(String phoneHash);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
