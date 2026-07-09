package com.nowgnodeel.retirement_planner.user.repository;

import com.nowgnodeel.retirement_planner.user.entity.AuthProvider;
import com.nowgnodeel.retirement_planner.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
