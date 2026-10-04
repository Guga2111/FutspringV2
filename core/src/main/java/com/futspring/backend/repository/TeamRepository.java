package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByDaily(Daily daily);

    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.players WHERE t.daily = :daily")
    List<Team> findByDailyWithPlayers(@Param("daily") Daily daily);
}
