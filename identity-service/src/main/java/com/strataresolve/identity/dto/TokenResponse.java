package com.strataresolve.identity.dto;

import lombok.*;

/**
 * DTO for authentication responses containing access and refresh tokens.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
}
