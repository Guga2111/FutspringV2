package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.LeagueTableEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueTableEntryRepository extends JpaRepository<LeagueTableEntry, Long> {

    List<LeagueTableEntry> findByDailyOrderByPositionAsc(Daily daily);
}
