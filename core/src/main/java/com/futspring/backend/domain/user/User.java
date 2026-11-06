package com.futspring.backend.domain.user;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String image;

    private String backgroundImage;

    private String position;

    // Incremented to revoke every JWT issued before (password reset); see AccessTokenVerifier
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    // Terms of use / privacy policy accepted at registration (LegalTerms); null for accounts created before V16
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    // Set when the account is deleted: the row is anonymized ("Ex-jogador") and kept for other people's history
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof User other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
