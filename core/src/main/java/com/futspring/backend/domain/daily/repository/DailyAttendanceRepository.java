package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyAttendanceId;
import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, DailyAttendanceId> {

    boolean existsByDailyAndUser(Daily daily, User user);

    long countByDaily(Daily daily);

    // The session's confirmed players with their users, in confirmation order
    @Query("SELECT a FROM DailyAttendance a JOIN FETCH a.user u WHERE a.daily = :daily ORDER BY a.confirmedAt, u.id")
    List<DailyAttendance> findByDailyWithUser(@Param("daily") Daily daily);

    /** The session's confirmed players, in confirmation order. */
    default List<User> findUsersByDaily(Daily daily) {
        return findByDailyWithUser(daily).stream().map(DailyAttendance::getUser).toList();
    }

    // [dailyId, confirmedPlayerCount] in one query (dailies without attendance are missing)
    @Query("SELECT a.daily.id, COUNT(a) FROM DailyAttendance a WHERE a.daily.id IN :ids GROUP BY a.daily.id")
    List<Object[]> countConfirmedByIds(@Param("ids") Collection<Long> ids);

    // Ids (among `ids`) of the dailies the user confirmed attendance in
    @Query("SELECT a.daily.id FROM DailyAttendance a WHERE a.daily.id IN :ids AND a.user.id = :userId")
    List<Long> findConfirmedDailyIds(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM DailyAttendance a WHERE a.daily = :daily AND a.user = :user")
    int deleteByDailyAndUser(@Param("daily") Daily daily, @Param("user") User user);

    /**
     * Removes the session's attendance through the persistence context (never a bulk delete: rows still managed
     * in the transaction would keep the daily from being deleted).
     */
    default void deleteAllOfDaily(Daily daily) {
        deleteAll(findByDailyWithUser(daily));
        flush();
    }
}
