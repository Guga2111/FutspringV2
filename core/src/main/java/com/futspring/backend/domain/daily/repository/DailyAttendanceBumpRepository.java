package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DailyAttendanceBumpRepository extends JpaRepository<DailyAttendanceBump, Long> {

    // Everything the bump e-mail needs, in one query (it runs after the commit, outside the transaction)
    @Query("""
        SELECT b FROM DailyAttendanceBump b
        JOIN FETCH b.user JOIN FETCH b.bumpedBy JOIN FETCH b.daily d JOIN FETCH d.pelada
        WHERE b.id = :id
        """)
    Optional<DailyAttendanceBump> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM DailyAttendanceBump b WHERE b.daily = :daily")
    void deleteByDaily(@Param("daily") Daily daily);
}
