package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface PeladaPlayerStarsRepository extends JpaRepository<PeladaPlayerStars, Long> {

    Optional<PeladaPlayerStars> findByPeladaAndUser(Pelada pelada, User user);

    List<PeladaPlayerStars> findByPeladaAndUserIn(Pelada pelada, Collection<User> users);

    // [userId, stars] of the members that have a row
    @Query("SELECT s.user.id, s.stars FROM PeladaPlayerStars s WHERE s.pelada = :pelada")
    List<Object[]> findStarsRowsByPelada(@Param("pelada") Pelada pelada);

    /** Stars by user id in one query; members missing from the map have {@link PeladaPlayerStars#DEFAULT_STARS}. */
    default Map<Long, Integer> findStarsByPelada(Pelada pelada) {
        return findStarsRowsByPelada(pelada).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
    }

    // Average of the user's stars over every pelada they belong to (no row counts as 3); null without peladas
    @Query(value = """
        SELECT AVG(COALESCE(s.stars, 3)) FROM pelada_members m
        LEFT JOIN pelada_player_stars s ON s.pelada_id = m.pelada_id AND s.user_id = m.user_id
        WHERE m.user_id = :userId
        """, nativeQuery = true)
    Double averageStarsByUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PeladaPlayerStars s WHERE s.pelada = :pelada AND s.user IN :users")
    void deleteByPeladaAndUserIn(@Param("pelada") Pelada pelada, @Param("users") Collection<User> users);

    @Modifying
    @Query("DELETE FROM PeladaPlayerStars s WHERE s.pelada = :pelada")
    void deleteByPelada(@Param("pelada") Pelada pelada);
}
