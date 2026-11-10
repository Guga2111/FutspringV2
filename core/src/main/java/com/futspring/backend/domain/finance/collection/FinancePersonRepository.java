package com.futspring.backend.domain.finance.collection;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinancePersonRepository extends JpaRepository<FinancePerson, Long> {
    List<FinancePerson> findByPeladaId(Long id);
    Optional<FinancePerson> findByPeladaIdAndUserId(Long peladaId, Long userId);
    List<FinancePerson> findByPeladaIdAndUserIdIn(Long peladaId, Collection<Long> userIds);
    Optional<FinancePerson> findByIdAndPeladaId(Long id, Long peladaId);
    // Account deletion: the account's financial identity keeps its charges under the anonymous name
    @Modifying
    @Query("update FinancePerson p set p.name = :name where p.userId = :userId")
    void renameUser(@Param("userId") Long userId, @Param("name") String name);
}
