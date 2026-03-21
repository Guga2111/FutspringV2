package com.futspring.backend.repository;

import com.futspring.backend.entity.Message;
import com.futspring.backend.entity.Pelada;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByPeladaOrderBySentAtDesc(Pelada pelada, Pageable pageable);
}
