package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

// A user's e-mail choice for one category. A row exists only when it differs from NotificationCategory.defaultEmail
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class NotificationPreference {

    @EmbeddedId
    @ToString.Include
    private NotificationPreferenceId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    public NotificationPreference(User user, NotificationCategory category, boolean emailEnabled) {
        this.id = new NotificationPreferenceId(user.getId(), category);
        this.user = user;
        this.emailEnabled = emailEnabled;
    }

    public NotificationCategory getCategory() {
        return id.getCategory();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NotificationPreference other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
