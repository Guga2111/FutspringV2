package com.futspring.backend.domain.finance;
import com.futspring.backend.domain.pelada.PeladaRef;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceManagerRepository extends JpaRepository<FinanceManager, Long> {
    boolean existsByPeladaIdAndUserId(Long peladaId, Long userId);
    List<FinanceManager> findByPeladaId(Long peladaId);
    void deleteByPeladaIdAndUserIdIn(Long peladaId, Collection<Long> userIds);
    @Query("select m.peladaId from FinanceManager m where m.userId = :userId order by m.peladaId")
    List<Long> findPeladaIdsByUserId(@Param("userId") Long userId);
    /** Peladas whose only responsável financeiro is the user (account deletion is blocked there). */
    @Query("""
        select new com.futspring.backend.domain.pelada.PeladaRef(p.id, p.name) from Pelada p
        where p.id in (select m.peladaId from FinanceManager m where m.userId = :userId)
          and (select count(o) from FinanceManager o where o.peladaId = p.id) = 1
        order by p.id
        """)
    List<PeladaRef> findSoleManagedBy(@Param("userId") Long userId);
    @Modifying
    @Query("delete from FinanceManager m where m.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
