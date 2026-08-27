package com.strataresolve.common.security.ratelimit;

public interface RateLimiter {
    boolean allowRequest(String key);
}