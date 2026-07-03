package com.strataresolve.user.service;

import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.user.domain.User;
import com.strataresolve.user.dto.RegisterRequest;
import com.strataresolve.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, 900000L);
    }

    @Test
    void register_withValidRequest_createsUserWithHashedPassword() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("StrongPass1!")
                .firstName("John")
                .lastName("Doe")
                .phone("+60123456789")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("$2a$10$hashedpassword");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getPhone()).isEqualTo("+60123456789");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void register_withDuplicateEmail_throwsDuplicateResourceException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("StrongPass1!")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existing@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withShortPassword_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Sh1!")
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least 8 characters");

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNoUppercase_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("lowercase1!")
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("uppercase");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNoLowercase_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("UPPERCASE1!")
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("lowercase");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNoDigit_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("NoDigits!!")
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("digit");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNoSpecialChar_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("NoSpecial1")
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("special character");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNullPassword_throwsBusinessRuleViolation() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password(null)
                .firstName("John")
                .lastName("Doe")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least 8 characters");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordIsHashedNotStoredAsPlaintext() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("ValidPass1!")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass1!")).thenReturn("$2a$10$encoded_hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$encoded_hash");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("ValidPass1!");
        verify(passwordEncoder).encode("ValidPass1!");
    }

    @Test
    void register_withNullPhone_createsUserSuccessfully() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("StrongPass1!")
                .firstName("John")
                .lastName("Doe")
                .phone(null)
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getPhone()).isNull();
        assertThat(result.isActive()).isTrue();
    }
}
