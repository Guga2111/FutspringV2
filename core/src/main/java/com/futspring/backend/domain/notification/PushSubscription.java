package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A browser/device's Web Push subscription: the push service URL it gave us and its encryption keys (RFC 8291)
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class PushSubscription {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "endpoint", nullable = false, unique = true, length = 1024)
    private String endpoint;

    // The browser's P-256 public key, base64url (65 bytes uncompressed)
    @Column(name = "p256dh", nullable = false, length = 200)
    private String p256dh;

    // The browser's auth secret, base64url (16 bytes)
    @Column(name = "auth", nullable = false, length = 50)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    public PushSubscription(User user, String endpoint, String p256dh, String auth, Instant createdAt) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PushSubscription other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
