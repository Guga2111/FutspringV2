package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "daily_award_puskas",
        joinColumns = @JoinColumn(name = "daily_award_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private List<User> puskasWinners = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "daily_award_wiltball",
        joinColumns = @JoinColumn(name = "daily_award_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private List<User> wiltballWinners = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "daily_award_artilheiro",
        joinColumns = @JoinColumn(name = "daily_award_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private List<User> artilheiroWinners = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "daily_award_garcom",
        joinColumns = @JoinColumn(name = "daily_award_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private List<User> garcomWinners = new ArrayList<>();
}
