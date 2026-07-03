package com.strataresolve.user.controller;

import com.strataresolve.user.dto.LoginRequest;
import com.strataresolve.user.dto.RefreshRequest;
import com.strataresolve.user.dto.RegisterRequest;
import com.strataresolve.user.dto.TokenResponse;
import com.strataresolve.user.service.AuthService;
import com.strataresolve.user.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for authentication endpoints.
 * All endpoints are under /api/auth/ and are publicly accessible (no JWT required).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * Register a new user account and return tokens.
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.registerAndLogin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate with email and password, returning access and refresh tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh an access token using a valid refresh token.
     * The refresh token is rotated: old one becomes invalid, new one is issued.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Logout the current user by revoking all their refresh tokens.
     * Requires a valid access token in the Authorization header.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = extractTokenFromHeader(authHeader);
        UUID userId = jwtService.extractUserId(token);
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new com.strataresolve.shared.exception.AuthenticationRequiredException(
                "Missing or invalid Authorization header");
    }
}
