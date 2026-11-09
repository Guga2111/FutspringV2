package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Row locks on every pelada the user belongs to, in id order (like the member routes, so two lockers never
     * deadlock), returning only the ids: the account deletion reads a constant number of rows whatever the count.
     */
    @Query(value = """
        SELECT p.id FROM peladas p
        WHERE p.id IN (SELECT m.pelada_id FROM pelada_members m WHERE m.user_id = :userId)
        ORDER BY p.id
        FOR UPDATE
        """, nativeQuery = true)
    List<Long> lockIdsByMemberId(@Param("userId") Long userId);

    @Query("""
        SELECT new com.futspring.backend.domain.pelada.PeladaRef(p.id, p.name)
        FROM Pelada p WHERE p.creator.id = :userId ORDER BY p.id
        """)
    List<PeladaRef> findCreatedBy(@Param("userId") Long userId);

    // [creatorId, peladaId, name] of the peladas created by any of `userIds`, in one query (platform admin)
    @Query("SELECT p.creator.id, p.id, p.name FROM Pelada p WHERE p.creator.id IN :userIds ORDER BY p.id")
    List<Object[]> findCreatedByAny(@Param("userIds") Collection<Long> userIds);

    // [userId, number of peladas they belong to] for `userIds`, in one query (platform admin)
    @Query("SELECT m.id, COUNT(p) FROM Pelada p JOIN p.members m WHERE m.id IN :userIds GROUP BY m.id")
    List<Object[]> countMembershipsByUsers(@Param("userIds") Collection<Long> userIds);

    // Account deletion: leaves every pelada at once (the join tables have no entity)
    @Modifying
    @Query(value = "DELETE FROM pelada_admins WHERE user_id = :userId", nativeQuery = true)
    void deleteAdminRowsOfUser(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM pelada_members WHERE user_id = :userId", nativeQuery = true)
    void deleteMemberRowsOfUser(@Param("userId") Long userId);
}
