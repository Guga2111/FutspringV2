package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_match_stats")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerMatchStat {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private int goals = 0;

    @Column(nullable = false)
    @Builder.Default
    private int assists = 0;

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerMatchStat other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
