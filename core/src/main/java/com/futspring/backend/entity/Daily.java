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
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Daily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pelada_id", nullable = false)
    private Pelada pelada;

    @Column(nullable = false)
    private LocalDate dailyDate;

    @Column(nullable = false)
    private String dailyTime;

    @Column(nullable = false)
    @Builder.Default
    private String status = "SCHEDULED";

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
}
