package com.futspring.backend.repository;

import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeladaRepository extends JpaRepository<Pelada, Long> {

    List<Pelada> findByMembersContaining(User user);

    List<Pelada> findByAutoCreateDailyEnabledTrue();
}
