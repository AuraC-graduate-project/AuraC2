package com.server.contestControl.authServer.repository;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UserRepository  extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String email);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    Optional<User> findFirstByRole(Role role);
}
