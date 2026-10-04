package com.futspring.backend.domain.stats.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.stats.entity.UserDailyStats;
import com.futspring.backend.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface UserDailyStatsRepository extends JpaRepository<UserDailyStats, Long> {

    List<UserDailyStats> findByDaily(Daily daily);

    // Profile history and timeline, with daily and pelada fetched to avoid N+1.
    // The *InPeladas variants restrict the rows to the peladas a non-owner viewer shares with the player.
    @Query("""
        SELECT uds FROM UserDailyStats uds
        JOIN FETCH uds.daily d JOIN FETCH d.pelada
        WHERE uds.user = :user
        ORDER BY d.dailyDate DESC, d.id DESC
        """)
    List<UserDailyStats> findHistoryByUser(@Param("user") User user);

    @Query("""
        SELECT uds FROM UserDailyStats uds
        JOIN FETCH uds.daily d JOIN FETCH d.pelada p
        WHERE uds.user = :user AND p.id IN :peladaIds
        ORDER BY d.dailyDate DESC, d.id DESC
        """)
    List<UserDailyStats> findHistoryByUserInPeladas(@Param("user") User user, @Param("peladaIds") Collection<Long> peladaIds);

    @Query("""
        SELECT uds FROM UserDailyStats uds
        JOIN FETCH uds.daily d
        WHERE uds.user = :user AND d.dailyDate >= :from AND d.dailyDate <= :to
        ORDER BY d.dailyDate ASC
        """)
    List<UserDailyStats> findByUserAndDateRange(@Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT uds FROM UserDailyStats uds
        JOIN FETCH uds.daily d
        WHERE uds.user = :user AND d.dailyDate >= :from AND d.dailyDate <= :to AND d.pelada.id IN :peladaIds
        ORDER BY d.dailyDate ASC
        """)
    List<UserDailyStats> findByUserAndDateRangeInPeladas(@Param("user") User user, @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to, @Param("peladaIds") Collection<Long> peladaIds);

    @Query("SELECT uds FROM UserDailyStats uds JOIN FETCH uds.user WHERE uds.daily = :daily")
    List<UserDailyStats> findByDailyWithUser(@Param("daily") Daily daily);

    @Query("SELECT COALESCE(SUM(uds.wins), 0) FROM UserDailyStats uds WHERE uds.user = :user AND uds.daily.pelada = :pelada")
    int sumMatchWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

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

    // Per-session history of one player in one pelada (newest first), daily fetched to avoid N+1.
    // Pageable limits the rows (PageRequest.of(0, n) = last n sessions; Pageable.unpaged() = all)
    @Query("""
        SELECT uds FROM UserDailyStats uds
        JOIN FETCH uds.daily d
        WHERE uds.user.id = :userId AND d.pelada.id = :peladaId
        ORDER BY d.dailyDate DESC, d.id DESC
        """)
    List<UserDailyStats> findHistoryByUserAndPelada(@Param("userId") Long userId, @Param("peladaId") Long peladaId, Pageable pageable);

    @Query("SELECT COUNT(uds) FROM UserDailyStats uds WHERE uds.user.id = :userId AND uds.daily.pelada.id = :peladaId")
    long countHistoryByUserAndPelada(@Param("userId") Long userId, @Param("peladaId") Long peladaId);
}
