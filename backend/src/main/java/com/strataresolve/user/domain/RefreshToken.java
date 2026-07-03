package com.strataresolve.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a refresh token used for JWT token rotation.
 * Each refresh token is single-use: on refresh, a new token is issued and the old one is revoked.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Check if this refresh token has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiryDate);
    }

    /**
     * Revoke this refresh token so it can no longer be used.
     */
    public void revoke() {
        this.isRevoked = true;
    }
}
