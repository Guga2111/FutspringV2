package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PeladaRepository extends JpaRepository<Pelada, Long> {

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

    @Query("SELECT p.id FROM Pelada p JOIN p.members m WHERE m.id = :userId")
    List<Long> findIdsByMemberId(@Param("userId") Long userId);

    // [peladaId, memberCount] in one query
    @Query("SELECT p.id, SIZE(p.members) FROM Pelada p WHERE p.id IN :ids")
    List<Object[]> countMembersByIds(@Param("ids") Collection<Long> ids);
}
