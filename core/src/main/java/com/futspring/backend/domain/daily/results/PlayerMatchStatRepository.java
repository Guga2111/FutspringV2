package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.results.Match;
import com.futspring.backend.domain.daily.results.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {

    @Query("SELECT p FROM PlayerMatchStat p JOIN FETCH p.user WHERE p.match IN :matches")
    List<PlayerMatchStat> findByMatchInWithUser(@Param("matches") List<Match> matches);

    @Modifying
    @Query("DELETE FROM PlayerMatchStat p WHERE p.match IN :matches")
    void deleteByMatchIn(@Param("matches") Collection<Match> matches);
}
