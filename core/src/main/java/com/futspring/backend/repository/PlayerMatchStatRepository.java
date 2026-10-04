package com.futspring.backend.repository;

import com.futspring.backend.entity.Match;
import com.futspring.backend.entity.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {

    List<PlayerMatchStat> findByMatch(Match match);

    @Query("SELECT p FROM PlayerMatchStat p JOIN FETCH p.user WHERE p.match IN :matches")
    List<PlayerMatchStat> findByMatchInWithUser(@Param("matches") List<Match> matches);

    @Modifying
    @Query("DELETE FROM PlayerMatchStat p WHERE p.match = :match")
    void deleteByMatch(@Param("match") Match match);
}
