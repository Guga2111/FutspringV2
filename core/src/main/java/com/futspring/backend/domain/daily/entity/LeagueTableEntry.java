package com.futspring.backend.domain.daily.entity;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "league_table_entries")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeagueTableEntry {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_id", nullable = false)
    private Daily daily;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(nullable = false)
    @Builder.Default
    private int wins = 0;

    @Column(nullable = false)
    @Builder.Default
    private int draws = 0;

    @Column(nullable = false)
    @Builder.Default
    private int losses = 0;

    @Column(nullable = false)
    @Builder.Default
    private int goalsFor = 0;

    @Column(nullable = false)
    @Builder.Default
    private int goalsAgainst = 0;

    @Column(nullable = false)
    @Builder.Default
    private int points = 0;

    @Override
    public boolean equals(Object o) {
        return o instanceof LeagueTableEntry other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
