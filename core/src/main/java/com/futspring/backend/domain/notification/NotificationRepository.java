package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // The recipient's page with everything the DTO needs; to-one fetch joins, so paging stays in the database
    @Query("""
        SELECT n FROM Notification n
        LEFT JOIN FETCH n.pelada LEFT JOIN FETCH n.daily LEFT JOIN FETCH n.actor
        WHERE n.recipient.id = :recipientId AND (:unreadOnly = false OR n.readAt IS NULL)
        ORDER BY n.createdAt DESC, n.id DESC
        """)
    List<Notification> findPage(@Param("recipientId") Long recipientId, @Param("unreadOnly") boolean unreadOnly,
                                Pageable pageable);

    // New notifications with everything the push needs (the recipient's e-mail and the DTO's references)
    @Query("""
        SELECT n FROM Notification n
        JOIN FETCH n.recipient LEFT JOIN FETCH n.pelada LEFT JOIN FETCH n.daily LEFT JOIN FETCH n.actor
        WHERE n.id IN :ids
        """)
    List<Notification> findAllByIdInWithRefs(@Param("ids") Collection<Long> ids);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    // [recipientId, unread count] for these recipients, one query (pushed with each notification)
    @Query("""
        SELECT n.recipient.id, COUNT(n) FROM Notification n
        WHERE n.recipient.id IN :recipientIds AND n.readAt IS NULL
        GROUP BY n.recipient.id
        """)
    List<Object[]> countUnreadByRecipients(@Param("recipientIds") Collection<Long> recipientIds);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    // Daily ids of the recipient's unread notifications of a type (read-all → the bumps of those sessions)
    @Query("""
        SELECT DISTINCT n.daily.id FROM Notification n
        WHERE n.recipient.id = :recipientId AND n.type = :type AND n.readAt IS NULL AND n.daily IS NOT NULL
        """)
    List<Long> findUnreadDailyIds(@Param("recipientId") Long recipientId, @Param("type") NotificationType type);

    // Unread notifications of a repeatable type that a new event collapses into
    @Query("""
        SELECT n FROM Notification n
        WHERE n.type = :type AND n.daily = :daily AND n.recipient IN :recipients AND n.readAt IS NULL
        """)
    List<Notification> findUnread(@Param("type") NotificationType type, @Param("daily") Daily daily,
                                  @Param("recipients") Collection<User> recipients);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Notification n SET n.readAt = :now
        WHERE n.recipient = :recipient AND n.type = :type AND n.daily = :daily AND n.readAt IS NULL
        """)
    int markRead(@Param("recipient") User recipient, @Param("type") NotificationType type,
                 @Param("daily") Daily daily, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.daily = :daily")
    void deleteByDaily(@Param("daily") Daily daily);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.pelada = :pelada")
    void deleteByPelada(@Param("pelada") Pelada pelada);
}
