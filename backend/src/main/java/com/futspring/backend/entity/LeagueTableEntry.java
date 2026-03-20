package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "league_table_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeagueTableEntry {

    @Id
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
}
