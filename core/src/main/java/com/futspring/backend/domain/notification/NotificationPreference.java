package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

// A user's e-mail and push choices for one category. A row exists only when one of them differs from the
// category's default: emailEnabled from defaultEmail, or pushEnabled non-null (null = defaultPush)
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

    // Null = NotificationCategory.defaultPush; only a value different from the default is stored
    @Column(name = "push_enabled")
    private Boolean pushEnabled;

    public NotificationPreference(User user, NotificationCategory category, boolean emailEnabled, Boolean pushEnabled) {
        this.id = new NotificationPreferenceId(user.getId(), category);
        this.user = user;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }

    public boolean pushEnabledOrDefault() {
        return pushEnabled != null ? pushEnabled : getCategory().defaultPush();
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
