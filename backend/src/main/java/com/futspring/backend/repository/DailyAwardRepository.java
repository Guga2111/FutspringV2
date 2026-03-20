package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DailyAwardRepository extends JpaRepository<DailyAward, Long> {

    Optional<DailyAward> findByDaily(Daily daily);
}
