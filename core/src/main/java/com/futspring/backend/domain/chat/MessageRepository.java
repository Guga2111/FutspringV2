package com.futspring.backend.domain.chat;

import com.futspring.backend.domain.pelada.Pelada;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.pelada = :pelada ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findByPeladaOrderBySentAtDesc(@Param("pelada") Pelada pelada, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.pelada = :pelada")
    void deleteByPelada(@Param("pelada") Pelada pelada);
}
