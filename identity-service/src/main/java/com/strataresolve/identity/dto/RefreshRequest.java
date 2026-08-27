package com.strataresolve.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO for token refresh requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
