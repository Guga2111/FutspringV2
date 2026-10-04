package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.pelada.Pelada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DailyRepository extends JpaRepository<Daily, Long> {

    boolean existsByPeladaAndDailyDate(Pelada pelada, LocalDate date);

    List<Daily> findByPeladaOrderByDailyDateDesc(Pelada pelada);

    List<Daily> findByPelada(Pelada pelada);

    // [dailyId, confirmedPlayerCount] in one query
    @Query("SELECT d.id, SIZE(d.confirmedPlayers) FROM Daily d WHERE d.id IN :ids")
    List<Object[]> countConfirmedByIds(@Param("ids") Collection<Long> ids);

    // [peladaId, next session date] for every pelada in one query
    @Query("""
        SELECT d.pelada.id, MIN(d.dailyDate) FROM Daily d
        WHERE d.pelada.id IN :peladaIds AND d.status IN :statuses AND d.dailyDate >= :from
        GROUP BY d.pelada.id
        """)
    List<Object[]> findNextDailyDates(@Param("peladaIds") Collection<Long> peladaIds,
                                      @Param("statuses") Collection<DailyStatus> statuses,
                                      @Param("from") LocalDate from);
}
