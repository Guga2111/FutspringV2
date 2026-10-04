package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "dailies")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Daily {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pelada_id", nullable = false)
    private Pelada pelada;

    @Column(nullable = false)
    private LocalDate dailyDate;

    @Column(nullable = false)
    private String dailyTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DailyStatus status = DailyStatus.SCHEDULED;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "daily_confirmed_players",
            joinColumns = @JoinColumn(name = "daily_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> confirmedPlayers = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean isFinished = false;

    private String championImage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        return o instanceof Daily other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
