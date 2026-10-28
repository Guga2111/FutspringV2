package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface PeladaMensalistaRepository extends JpaRepository<PeladaMensalista, Long> {

    boolean existsByPeladaAndUser(Pelada pelada, User user);

    long countByPelada(Pelada pelada);

    @Query("SELECT m.user.id FROM PeladaMensalista m WHERE m.pelada = :pelada")
    List<Long> findUserIdRowsByPelada(@Param("pelada") Pelada pelada);

    /** Ids of the pelada's mensalistas in one query. */
    default Set<Long> findUserIdsByPelada(Pelada pelada) {
        return new HashSet<>(findUserIdRowsByPelada(pelada));
    }

    // Ids (among `peladaIds`) of the peladas where the user is a mensalista
    @Query("SELECT m.pelada.id FROM PeladaMensalista m WHERE m.user.id = :userId AND m.pelada.id IN :peladaIds")
    List<Long> findPeladaIdsByUserAndPeladaIn(@Param("userId") Long userId,
                                              @Param("peladaIds") Collection<Long> peladaIds);

    @Modifying
    @Query("DELETE FROM PeladaMensalista m WHERE m.pelada = :pelada AND m.user IN :users")
    void deleteByPeladaAndUserIn(@Param("pelada") Pelada pelada, @Param("users") Collection<User> users);

    @Modifying
    @Query("DELETE FROM PeladaMensalista m WHERE m.pelada = :pelada")
    void deleteByPelada(@Param("pelada") Pelada pelada);
}
