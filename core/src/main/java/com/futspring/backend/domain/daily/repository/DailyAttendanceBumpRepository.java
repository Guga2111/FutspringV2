package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DailyAttendanceBumpRepository extends JpaRepository<DailyAttendanceBump, Long> {

    // The session's bump history, newest first
    @Query("""
        SELECT b FROM DailyAttendanceBump b JOIN FETCH b.user JOIN FETCH b.bumpedBy
        WHERE b.daily = :daily ORDER BY b.bumpedAt DESC, b.id DESC
        """)
    List<DailyAttendanceBump> findByDailyWithUsers(@Param("daily") Daily daily);

    // The user's bumps not dismissed yet in any of the sessions, newest first (one query for /peladas/my)
    @Query("""
        SELECT b FROM DailyAttendanceBump b JOIN FETCH b.bumpedBy
        WHERE b.user.id = :userId AND b.daily.id IN :dailyIds AND b.seenAt IS NULL
        ORDER BY b.bumpedAt DESC, b.id DESC
        """)
    List<DailyAttendanceBump> findUnseenByUserAndDailyIn(@Param("userId") Long userId,
                                                        @Param("dailyIds") Collection<Long> dailyIds);

    @Modifying
    @Query("UPDATE DailyAttendanceBump b SET b.seenAt = :now WHERE b.daily = :daily AND b.user = :user AND b.seenAt IS NULL")
    int markSeen(@Param("daily") Daily daily, @Param("user") User user, @Param("now") Instant now);

    // The user's bumps of several sessions at once (reading every notification)
    @Modifying
    @Query("""
        UPDATE DailyAttendanceBump b SET b.seenAt = :now
        WHERE b.user = :user AND b.daily.id IN :dailyIds AND b.seenAt IS NULL
        """)
    int markSeenInDailies(@Param("user") User user, @Param("dailyIds") Collection<Long> dailyIds,
                          @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM DailyAttendanceBump b WHERE b.daily = :daily")
    void deleteByDaily(@Param("daily") Daily daily);
}
