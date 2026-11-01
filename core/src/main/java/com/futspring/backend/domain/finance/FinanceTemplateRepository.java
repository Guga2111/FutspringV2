package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceTemplateRepository extends JpaRepository<FinanceTemplate, Long> {
    @EntityGraph(attributePaths = "components")
    List<FinanceTemplate> findByPeladaIdOrderById(Long peladaId);
    Optional<FinanceTemplate> findByIdAndPeladaId(Long id, Long peladaId);
}
