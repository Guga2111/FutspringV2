package com.futspring.backend.repository;

import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RankingRepository extends JpaRepository<Ranking, Long> {
    List<Ranking> findByPelada(Pelada pelada);
    Optional<Ranking> findByPeladaAndUser(Pelada pelada, User user);
    List<Ranking> findByPeladaAndUserIn(Pelada pelada, Collection<User> users);
}
