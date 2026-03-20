package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "daily_awards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_id", nullable = false, unique = true)
    private Daily daily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puskas_winner_id")
    private User puskasWinner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wiltball_winner_id")
    private User wiltballWinner;
}
