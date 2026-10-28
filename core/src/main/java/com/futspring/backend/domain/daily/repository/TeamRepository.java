package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByDaily(Daily daily);

    long countByDaily(Daily daily);

    // [dailyId, team count] in one query
    @Query("SELECT t.daily.id, COUNT(t) FROM Team t WHERE t.daily.id IN :ids GROUP BY t.daily.id")
    List<Object[]> countByDailyIds(@Param("ids") Collection<Long> ids);

    // LEFT JOIN so a team without players is still returned
    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.players WHERE t.daily = :daily ORDER BY t.id")
    List<Team> findByDailyWithPlayers(@Param("daily") Daily daily);

    // [userId, dailyId, name, color] of each user's team in each of these sessions (notifications), one query
    @Query("""
        SELECT p.id, t.daily.id, t.name, t.color FROM Team t JOIN t.players p
        WHERE p.id IN :userIds AND t.daily.id IN :dailyIds
        """)
    List<Object[]> findTeamsOfUsersInDailies(@Param("userIds") Collection<Long> userIds,
                                             @Param("dailyIds") Collection<Long> dailyIds);

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.players WHERE t.daily IN :dailies")
    List<Team> findByDailyInWithPlayers(@Param("dailies") Collection<Daily> dailies);
}
