package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.pelada.Pelada;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyRepository extends JpaRepository<Daily, Long> {

    // Row lock on the session: confirmations of the same session wait for each other (capacity count and
    // bump victim never race), other sessions don't block. Held until the transaction commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Daily d WHERE d.id = :id")
    Optional<Daily> findByIdForUpdate(@Param("id") Long id);

    boolean existsByPeladaAndDailyDate(Pelada pelada, LocalDate date);

    List<Daily> findByPeladaOrderByDailyDateDesc(Pelada pelada);

    List<Daily> findByPelada(Pelada pelada);

    // Sessions of the pelada with `status` dated in [from, to] (attendance ranking denominator)
    @Query("SELECT COUNT(d) FROM Daily d WHERE d.pelada.id = :peladaId AND d.status = :status AND d.dailyDate BETWEEN :from AND :to")
    long countByPeladaAndStatusInRange(@Param("peladaId") Long peladaId, @Param("status") DailyStatus status,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    // The next session (earliest date from `from` on, in one of `statuses`) of every pelada, in one query
    @Query("""
        SELECT d FROM Daily d
        WHERE d.pelada.id IN :peladaIds AND d.status IN :statuses AND d.dailyDate >= :from
          AND d.dailyDate = (
            SELECT MIN(d2.dailyDate) FROM Daily d2
            WHERE d2.pelada = d.pelada AND d2.status IN :statuses AND d2.dailyDate >= :from)
        """)
    List<Daily> findNextDailies(@Param("peladaIds") Collection<Long> peladaIds,
                                @Param("statuses") Collection<DailyStatus> statuses,
                                @Param("from") LocalDate from);
}
