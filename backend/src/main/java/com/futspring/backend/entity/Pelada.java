package com.futspring.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "peladas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pelada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dayOfWeek;

    @Column(nullable = false)
    private String timeOfDay;

    @Column(nullable = false)
    private Float duration;

    private String address;

    private String reference;

    private String image;

    @Column(nullable = false)
    @Builder.Default
    private boolean autoCreateDailyEnabled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pelada_members",
            joinColumns = @JoinColumn(name = "pelada_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pelada_admins",
            joinColumns = @JoinColumn(name = "pelada_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> admins = new HashSet<>();
}
