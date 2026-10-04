package com.futspring.backend.repository;

import com.futspring.backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    // Matches username or email, but callers must only return public fields (PublicUserDTO)
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY u.username
        """)
    List<User> searchByUsernameOrEmail(@Param("q") String q, Pageable pageable);
}
