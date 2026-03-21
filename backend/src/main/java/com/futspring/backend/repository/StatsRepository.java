package com.futspring.backend.repository;

import com.futspring.backend.entity.Stats;
import com.futspring.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatsRepository extends JpaRepository<Stats, Long> {
    Optional<Stats> findByUser(User user);
}
