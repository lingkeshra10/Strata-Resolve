package com.strataresolve.common.security;

import java.util.UUID;

public interface JwtTokenService {

    boolean isTokenValid(String token);

    UUID extractUserId(String token);

    String extractEmail(String token);
}
