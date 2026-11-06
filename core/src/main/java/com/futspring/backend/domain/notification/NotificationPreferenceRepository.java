package com.futspring.backend.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    List<NotificationPreference> findByIdUserId(Long userId);

    // Every recipient of a batch in one query (the e-mail dispatch)
    List<NotificationPreference> findByIdUserIdIn(Collection<Long> userIds);

    // Account deletion
    @Modifying
    @Query("DELETE FROM NotificationPreference p WHERE p.id.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
