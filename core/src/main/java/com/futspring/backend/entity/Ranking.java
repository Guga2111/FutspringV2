package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rankings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"pelada_id", "user_id"})
})
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ranking {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pelada_id", nullable = false)
    private Pelada pelada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
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

    @Override
    public boolean equals(Object o) {
        return o instanceof Ranking other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
