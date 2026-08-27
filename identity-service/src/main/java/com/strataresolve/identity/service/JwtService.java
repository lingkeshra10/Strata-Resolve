package com.strataresolve.identity.service;

import com.strataresolve.common.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating and validating JWT access tokens.
 * Access tokens are short-lived (default 15 minutes) and stateless.
 */
@Service
public class JwtService implements JwtTokenService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    /**
     * Generate a new JWT access token for the given user.
     *
     * @param userId the user's unique identifier
     * @param email  the user's email address (stored as subject)
     * @return the signed JWT token string
     */
    public String generateAccessToken(UUID userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extract the email (subject) from a valid JWT token.
     *
     * @param token the JWT token string
     * @return the email (subject) from the token
     * @throws JwtException if the token is invalid or expired
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extract the user ID from a valid JWT token.
     *
     * @param token the JWT token string
     * @return the user ID from the token claims
     * @throws JwtException if the token is invalid or expired
     */
    public UUID extractUserId(String token) {
        String userId = extractClaims(token).get("userId", String.class);
        return UUID.fromString(userId);
    }

    /**
     * Validate a JWT token: check signature and expiration.
     *
     * @param token the JWT token string
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Check if a token is expired.
     *
     * @param token the JWT token string
     * @return true if the token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            extractClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
