package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface UserDailyStatsRepository extends JpaRepository<UserDailyStats, Long> {

    List<UserDailyStats> findByDaily(Daily daily);

    List<UserDailyStats> findByUserOrderByDailyDailyDateDesc(User user);

    @Query("SELECT uds FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.dailyDate >= :from AND uds.daily.dailyDate <= :to ORDER BY uds.daily.dailyDate ASC")
    List<UserDailyStats> findByUserAndDateRange(@Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // Global aggregates (for Stats rebuild)
    @Query("SELECT COALESCE(SUM(uds.goals), 0) FROM UserDailyStats uds WHERE uds.user = :user")
    int sumGoalsByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(uds.assists), 0) FROM UserDailyStats uds WHERE uds.user = :user")
    int sumAssistsByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(uds.matchesPlayed), 0) FROM UserDailyStats uds WHERE uds.user = :user")
    int sumMatchesPlayedByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(uds.wins), 0) FROM UserDailyStats uds WHERE uds.user = :user")
    int sumMatchWinsByUser(@Param("user") User user);

    @Query("SELECT COUNT(uds) FROM UserDailyStats uds WHERE uds.user = :user")
    long countSessionsByUser(@Param("user") User user);

    @Query("SELECT COUNT(uds) FROM UserDailyStats uds WHERE uds.user = :user AND uds.wonSession = true")
    long countSessionWinsByUser(@Param("user") User user);

    // Pelada-scoped aggregates (for Ranking rebuild)
    @Query("SELECT COALESCE(SUM(uds.goals), 0) FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.pelada = :pelada")
    int sumGoalsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COALESCE(SUM(uds.assists), 0) FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.pelada = :pelada")
    int sumAssistsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COALESCE(SUM(uds.matchesPlayed), 0) FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.pelada = :pelada")
    int sumMatchesPlayedByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COUNT(uds) FROM UserDailyStats uds WHERE uds.user = :user AND uds.wonSession = true AND uds.daily.pelada = :pelada")
    long countSessionWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    // Batch aggregate for Ranking rebuild (pelada-scoped)
    @Query("""
        SELECT uds.user.id,
               COALESCE(SUM(uds.goals), 0),
               COALESCE(SUM(uds.assists), 0),
               COALESCE(SUM(uds.matchesPlayed), 0),
               SUM(CASE WHEN uds.wonSession = true THEN 1 ELSE 0 END)
        FROM UserDailyStats uds
        WHERE uds.user IN :users AND uds.daily.pelada = :pelada
        GROUP BY uds.user.id
        """)
    List<Object[]> aggregateRankingByUsersAndPelada(@Param("users") Collection<User> users, @Param("pelada") Pelada pelada);

    // Batch aggregate for Stats rebuild (global)
    @Query("""
        SELECT uds.user.id,
               COALESCE(SUM(uds.goals), 0),
               COALESCE(SUM(uds.assists), 0),
               COALESCE(SUM(uds.matchesPlayed), 0),
               COALESCE(SUM(uds.wins), 0),
               COUNT(uds),
               SUM(CASE WHEN uds.wonSession = true THEN 1 ELSE 0 END)
        FROM UserDailyStats uds
        WHERE uds.user IN :users
        GROUP BY uds.user.id
        """)
    List<Object[]> aggregateStatsByUsers(@Param("users") Collection<User> users);
}
