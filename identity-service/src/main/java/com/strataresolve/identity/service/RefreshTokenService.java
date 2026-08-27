package com.strataresolve.identity.service;

import com.strataresolve.common.exception.AuthenticationRequiredException;
import com.strataresolve.common.exception.TokenExpiredException;
import com.strataresolve.identity.domain.RefreshToken;
import com.strataresolve.identity.domain.User;
import com.strataresolve.identity.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
/**
 * Service managing refresh tokens with rotation and invalidation.
 * <p>
 * On refresh: the old token is revoked and a new token is issued (rotation).
 * On invalid/expired token: all tokens for that user are invalidated (Requirement 4.5).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Create a new refresh token for the given user.
     *
     * @param user the user to create the token for
     * @return the created RefreshToken entity
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpirationMs))
                .isRevoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Rotate the refresh token: revoke the old one, issue a new one.
     * If the presented token is invalid or expired, all user tokens are invalidated.
     *
     * @param tokenValue the current refresh token value
     * @return the new RefreshToken entity
     * @throws TokenExpiredException if the refresh token has expired
     * @throws AuthenticationRequiredException if the token is invalid or revoked
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String tokenValue) {
        RefreshToken existingToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> {
                    // Token not found - cannot determine user, just reject
                    return new AuthenticationRequiredException("Invalid refresh token");
                });

        // If the token is already revoked, this indicates reuse (potential theft).
        // Invalidate all tokens for this user as a security measure.
        if (existingToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(existingToken.getUser().getId());
            throw new AuthenticationRequiredException("Refresh token has been revoked. All sessions invalidated.");
        }

        // If the token is expired, invalidate all tokens for this user.
        if (existingToken.isExpired()) {
            refreshTokenRepository.revokeAllByUserId(existingToken.getUser().getId());
            throw new TokenExpiredException("Refresh token has expired. Please login again.");
        }

        // Revoke the current token (rotation)
        existingToken.revoke();
        refreshTokenRepository.save(existingToken);

        // Issue a new refresh token
        return createRefreshToken(existingToken.getUser());
    }

    /**
     * Revoke all refresh tokens for a user (used on logout).
     *
     * @param userId the user's ID
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }
}
