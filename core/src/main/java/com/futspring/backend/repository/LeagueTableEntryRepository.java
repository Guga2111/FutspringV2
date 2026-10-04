package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.LeagueTableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeagueTableEntryRepository extends JpaRepository<LeagueTableEntry, Long> {

    List<LeagueTableEntry> findByDailyOrderByPositionAsc(Daily daily);

    @Query("SELECT e FROM LeagueTableEntry e JOIN FETCH e.team WHERE e.daily = :daily ORDER BY e.position")
    List<LeagueTableEntry> findByDailyWithTeam(@Param("daily") Daily daily);
}
