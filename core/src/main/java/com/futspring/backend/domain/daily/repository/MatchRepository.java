package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByDaily(Daily daily);

    long countByDaily(Daily daily);

    // [dailyId, match count] in one query
    @Query("SELECT m.daily.id, COUNT(m) FROM Match m WHERE m.daily.id IN :ids GROUP BY m.daily.id")
    List<Object[]> countByDailyIds(@Param("ids") Collection<Long> ids);

    // Scoped lookup: a match id from the request must belong to the route's daily
    Optional<Match> findByIdAndDaily(Long id, Daily daily);

    @Query("""
        SELECT m FROM Match m
        JOIN FETCH m.team1 JOIN FETCH m.team2 LEFT JOIN FETCH m.winner
        WHERE m.daily = :daily
        ORDER BY m.id
        """)
    List<Match> findByDailyWithTeams(@Param("daily") Daily daily);
}
