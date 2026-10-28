package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PeladaRepository extends JpaRepository<Pelada, Long> {

    // Row lock on the pelada: two admins marking mensalistas at once can't go past the limit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pelada p WHERE p.id = :id")
    Optional<Pelada> findByIdForUpdate(@Param("id") Long id);

    List<Pelada> findByMembersContaining(User user);

    List<Pelada> findByAutoCreateDailyEnabledTrue();

    // Membership checks without loading the members/admins collections
    boolean existsByIdAndMembers_Id(Long id, Long userId);

    boolean existsByIdAndAdmins_Id(Long id, Long userId);

    boolean existsByIdAndMembers_Email(Long id, String email);

    @Query("""
        SELECT COUNT(p) > 0 FROM Pelada p
        JOIN p.members a JOIN p.members b
        WHERE a.id = :userA AND b.id = :userB
        """)
    boolean existsSharedPelada(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
        SELECT p FROM Pelada p
        JOIN p.members a JOIN p.members b
        WHERE a.id = :userA AND b.id = :userB
        ORDER BY p.name
        """)
    List<Pelada> findSharedPeladas(@Param("userA") Long userA, @Param("userB") Long userB);

    // Ids (among `userIds`) of the users who are members of the pelada, in one query
    @Query("SELECT m.id FROM Pelada p JOIN p.members m WHERE p.id = :peladaId AND m.id IN :userIds")
    List<Long> findMemberIds(@Param("peladaId") Long peladaId, @Param("userIds") Collection<Long> userIds);

    // Ids of the pelada's admins, in one query
    @Query("SELECT a.id FROM Pelada p JOIN p.admins a WHERE p.id = :peladaId")
    List<Long> findAdminIds(@Param("peladaId") Long peladaId);

    @Query("SELECT p.id FROM Pelada p JOIN p.members m WHERE m.id = :userId")
    List<Long> findIdsByMemberId(@Param("userId") Long userId);

    // [peladaId, memberCount] in one query
    // Ids (among `ids`) of the peladas the user administers
    @Query("SELECT p.id FROM Pelada p JOIN p.admins a WHERE p.id IN :ids AND a.id = :userId")
    List<Long> findAdministeredIds(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Query("SELECT p.id, SIZE(p.members) FROM Pelada p WHERE p.id IN :ids")
    List<Object[]> countMembersByIds(@Param("ids") Collection<Long> ids);
}
