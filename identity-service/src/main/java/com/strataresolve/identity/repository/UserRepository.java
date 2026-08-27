package com.strataresolve.identity.repository;

import com.strataresolve.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their email address (case-sensitive).
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user with the given email already exists.
     */
    boolean existsByEmail(String email);
}
