package com.futspring.backend.domain.pelada;

import com.futspring.backend.shared.entity.EntityIdentity;
import com.futspring.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "peladas")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pelada {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dayOfWeek;

    @Column(nullable = false)
    private String timeOfDay;

    @Column(nullable = false)
    private Float duration;

    private String address;

    private String reference;

    private String image;

    @Column(nullable = false)
    @Builder.Default
    private boolean autoCreateDailyEnabled = false;

    @Column(nullable = false)
    @Builder.Default
    private int numberOfTeams = 2;

    @Column(nullable = false)
    @Builder.Default
    private int playersPerTeam = 5;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pelada_members",
            joinColumns = @JoinColumn(name = "pelada_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pelada_admins",
            joinColumns = @JoinColumn(name = "pelada_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> admins = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        return o instanceof Pelada other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
