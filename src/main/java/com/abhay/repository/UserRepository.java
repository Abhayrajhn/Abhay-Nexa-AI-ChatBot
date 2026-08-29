package com.abhay.repository;

import com.abhay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity.
 *
 * Provides CRUD operations and custom queries for users.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username.
     *
     * @param username The username to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if a username already exists.
     *
     * @param username The username to check
     * @return true if username exists, false otherwise
     */
    boolean existsByUsername(String username);
}
