package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_daily_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_id", nullable = false)
    private Daily daily;

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

    @Column(nullable = false)
    @Builder.Default
    private boolean wonSession = false;
}
