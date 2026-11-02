package com.futspring.backend.domain.tour;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTourRepository extends JpaRepository<UserTour, UserTourId> {

    List<UserTour> findByIdUserId(Long userId);
}
