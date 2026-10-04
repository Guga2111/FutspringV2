package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyRepository extends JpaRepository<Daily, Long> {

    boolean existsByPeladaAndDailyDate(Pelada pelada, LocalDate date);

    List<Daily> findByPeladaOrderByDailyDateDesc(Pelada pelada);
}
