package com.futspring.backend.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    List<NotificationPreference> findByIdUserId(Long userId);

    // Every recipient of a batch in one query (the e-mail dispatch)
    List<NotificationPreference> findByIdUserIdIn(Collection<Long> userIds);
}
