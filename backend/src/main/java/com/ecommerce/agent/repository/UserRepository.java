package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByQqEmail(String qqEmail);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByQqEmailAndIdNot(String qqEmail, Long id);

    long countByRoleIgnoreCase(String role);

    long countByEnabledTrue();
}
