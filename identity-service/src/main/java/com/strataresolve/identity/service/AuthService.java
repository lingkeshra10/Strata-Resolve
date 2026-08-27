package com.strataresolve.identity.service;

import com.strataresolve.common.exception.AuthenticationRequiredException;
import com.strataresolve.common.exception.BusinessRuleViolationException;
import com.strataresolve.common.exception.DuplicateResourceException;
import com.strataresolve.identity.domain.RefreshToken;
import com.strataresolve.identity.domain.User;
import com.strataresolve.identity.dto.LoginRequest;
import com.strataresolve.identity.dto.RegisterRequest;
import com.strataresolve.identity.dto.TokenResponse;
import com.strataresolve.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Service handling user authentication operations including registration, login, and password management.
 */
@Service
public class AuthService {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final long accessTokenExpirationMs;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    /**
     * Register a new user account.
     *
     * @param request the registration details
     * @return the created User entity
     * @throws DuplicateResourceException if email already exists
     * @throws BusinessRuleViolationException if password does not meet complexity requirements
     */
    @Transactional
    public User register(RegisterRequest request) {
        validatePasswordComplexity(request.getPassword());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("A user with email '" + request.getEmail() + "' already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .isActive(true)
                .build();

        return userRepository.save(user);
    }

    /**
     * Register a new user and return tokens (combines register + login).
     *
     * @param request the registration details
     * @return token response with access and refresh tokens
     */
    @Transactional
    public TokenResponse registerAndLogin(RegisterRequest request) {
        User user = register(request);
        return generateTokenResponse(user);
    }

    /**
     * Authenticate user with email and password, returning access and refresh tokens.
     *
     * @param request the login credentials
     * @return token response with access and refresh tokens
     * @throws AuthenticationRequiredException if credentials are invalid
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationRequiredException("Invalid credentials"));

        if (!user.isActive()) {
            throw new AuthenticationRequiredException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationRequiredException("Invalid credentials");
        }

        return generateTokenResponse(user);
    }

    /**
     * Refresh the access token using a valid refresh token.
     * Implements token rotation: old refresh token is revoked, new one is issued.
     *
     * @param refreshTokenValue the current refresh token
     * @return token response with new access and refresh tokens
     */
    @Transactional
    public TokenResponse refreshToken(String refreshTokenValue) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshTokenValue);
        User user = newRefreshToken.getUser();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .build();
    }

    /**
     * Logout a user by revoking all their refresh tokens.
     *
     * @param userId the user's ID
     */
    @Transactional
    public void logout(java.util.UUID userId) {
        refreshTokenService.revokeAllUserTokens(userId);
    }

    private TokenResponse generateTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .build();
    }

    /**
     * Validates password complexity requirements:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }

        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessRuleViolationException(
                    "Password must contain at least one uppercase letter");
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessRuleViolationException(
                    "Password must contain at least one lowercase letter");
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw new BusinessRuleViolationException(
                    "Password must contain at least one digit");
        }

        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            throw new BusinessRuleViolationException(
                    "Password must contain at least one special character");
        }
    }
}
