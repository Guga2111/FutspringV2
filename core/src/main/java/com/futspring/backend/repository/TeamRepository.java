package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByDaily(Daily daily);

    // LEFT JOIN so a team without players is still returned
    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.players WHERE t.daily = :daily ORDER BY t.id")
    List<Team> findByDailyWithPlayers(@Param("daily") Daily daily);
}
