package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserDailyStatsRepository extends JpaRepository<UserDailyStats, Long> {

    List<UserDailyStats> findByDaily(Daily daily);

    List<UserDailyStats> findByUserOrderByDailyDailyDateDesc(User user);

    @Query("SELECT uds FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.dailyDate >= :from AND uds.daily.dailyDate <= :to ORDER BY uds.daily.dailyDate ASC")
    List<UserDailyStats> findByUserAndDateRange(@Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
