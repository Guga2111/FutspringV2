package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyAward;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyAwardRepository extends JpaRepository<DailyAward, Long> {

    Optional<DailyAward> findByDaily(Daily daily);

    @Query("SELECT COUNT(da) FROM DailyAward da WHERE :user MEMBER OF da.wiltballWinners")
    long countByWiltballWinnersContaining(@Param("user") User user);

    @Query("SELECT COUNT(da) FROM DailyAward da WHERE :user MEMBER OF da.puskasWinners")
    long countByPuskasWinnersContaining(@Param("user") User user);

    @Query("SELECT COUNT(da) FROM DailyAward da WHERE :user MEMBER OF da.artilheiroWinners")
    long countByArtilheiroWinnersContaining(@Param("user") User user);

    @Query("SELECT COUNT(da) FROM DailyAward da WHERE :user MEMBER OF da.garcomWinners")
    long countByGarcomWinnersContaining(@Param("user") User user);

    @Query("SELECT da FROM DailyAward da JOIN da.daily d WHERE d.pelada = :pelada")
    List<DailyAward> findAllByPelada(@Param("pelada") Pelada pelada);

    @Query("SELECT COUNT(da) FROM DailyAward da JOIN da.daily d WHERE d.pelada = :pelada AND :user MEMBER OF da.artilheiroWinners")
    long countArtilheiroWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COUNT(da) FROM DailyAward da JOIN da.daily d WHERE d.pelada = :pelada AND :user MEMBER OF da.garcomWinners")
    long countGarcomWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COUNT(da) FROM DailyAward da JOIN da.daily d WHERE d.pelada = :pelada AND :user MEMBER OF da.puskasWinners")
    long countPuskasWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT COUNT(da) FROM DailyAward da JOIN da.daily d WHERE d.pelada = :pelada AND :user MEMBER OF da.wiltballWinners")
    long countWiltballWinsByUserAndPelada(@Param("user") User user, @Param("pelada") Pelada pelada);

    @Query("SELECT da.daily.dailyDate FROM DailyAward da WHERE :user MEMBER OF da.puskasWinners")
    List<LocalDate> findPuskasDatesByUser(@Param("user") User user);

    @Query("SELECT u.id, d.dailyDate FROM DailyAward da JOIN da.puskasWinners u JOIN da.daily d WHERE u IN :users")
    List<Object[]> findPuskasDatesByUsers(@Param("users") Collection<User> users);
}
