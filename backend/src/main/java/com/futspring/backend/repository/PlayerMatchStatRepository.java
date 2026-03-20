package com.futspring.backend.repository;

import com.futspring.backend.entity.Match;
import com.futspring.backend.entity.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {

    List<PlayerMatchStat> findByMatch(Match match);
}
