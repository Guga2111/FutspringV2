package com.futspring.backend.domain.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // Every subscription of a batch's recipients, one query (the push dispatch)
    @Query("SELECT s FROM PushSubscription s WHERE s.user.id IN :userIds")
    List<PushSubscription> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    long countByUserId(Long userId);

    // The least recently alive subscriptions first (last success, else creation), never the one just sent: the ones
    // dropped past the limit
    @Query("""
        SELECT s FROM PushSubscription s WHERE s.user.id = :userId AND s.id <> :keepId
        ORDER BY COALESCE(s.lastSuccessAt, s.createdAt), s.id
        """)
    List<PushSubscription> findOldestByUserId(@Param("userId") Long userId, @Param("keepId") Long keepId, Limit limit);

    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.user.id = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    // Password reset/change and account deletion: the old devices must stop receiving
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    // Endpoints the push service answered 404/410 for
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.id IN :ids")
    void deleteByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("UPDATE PushSubscription s SET s.lastSuccessAt = :at WHERE s.id IN :ids")
    void markSuccess(@Param("ids") Collection<Long> ids, @Param("at") Instant at);
}
