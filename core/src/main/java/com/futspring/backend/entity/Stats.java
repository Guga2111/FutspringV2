package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stats")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stats {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private int goals = 0;

    @Column(nullable = false)
    @Builder.Default
    private int assists = 0;

    @Column(nullable = false)
    @Builder.Default
    private int matchesPlayed = 0;

    @Column(nullable = false)
    @Builder.Default
    private int wins = 0;

    @Column(nullable = false)
    @Builder.Default
    private int sessionsPlayed = 0;

    @Column(nullable = false)
    @Builder.Default
    private int matchWins = 0;

    @ElementCollection
    @CollectionTable(name = "stats_puskas_dates", joinColumns = @JoinColumn(name = "stats_id"))
    @Column(name = "puskas_date")
    @Builder.Default
    private List<LocalDate> puskasDates = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        return o instanceof Stats other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
