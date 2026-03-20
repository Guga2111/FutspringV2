package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.UserDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDailyStatsRepository extends JpaRepository<UserDailyStats, Long> {

    List<UserDailyStats> findByDaily(Daily daily);
}
