package com.futspring.backend.repository;

import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    @Query("SELECT r FROM Ranking r JOIN FETCH r.user WHERE r.pelada = :pelada")
    List<Ranking> findByPeladaWithUser(@Param("pelada") Pelada pelada);

    Optional<Ranking> findByPeladaAndUser(Pelada pelada, User user);

    List<Ranking> findByPeladaAndUserIn(Pelada pelada, Collection<User> users);

    @Modifying
    @Query("DELETE FROM Ranking r WHERE r.pelada = :pelada")
    void deleteByPelada(@Param("pelada") Pelada pelada);
}
