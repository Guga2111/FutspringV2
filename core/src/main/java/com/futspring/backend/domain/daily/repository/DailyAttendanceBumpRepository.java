package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyAttendanceBumpRepository extends JpaRepository<DailyAttendanceBump, Long> {

    @Modifying
    @Query("DELETE FROM DailyAttendanceBump b WHERE b.daily = :daily")
    void deleteByDaily(@Param("daily") Daily daily);
}
