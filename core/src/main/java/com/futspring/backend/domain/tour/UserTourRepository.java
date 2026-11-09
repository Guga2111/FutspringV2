package com.futspring.backend.domain.tour;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTourRepository extends JpaRepository<UserTour, UserTourId> {

    List<UserTour> findByIdUserId(Long userId);

    // Account deletion
    @Modifying
    @Query("DELETE FROM UserTour t WHERE t.id.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
