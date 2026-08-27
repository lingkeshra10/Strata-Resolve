package com.strataresolve.common.security.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowDurationMillis;

    private final Map<String, RateLimitEntry> requestCounts =
            new ConcurrentHashMap<>();

    public InMemoryRateLimiter(
            int maxRequests,
            Duration windowDuration
    ) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException(
                    "maxRequests must be greater than zero"
            );
        }

        if (windowDuration == null ||
                windowDuration.isZero() ||
                windowDuration.isNegative()) {

            throw new IllegalArgumentException(
                    "windowDuration must be greater than zero"
            );
        }

        this.maxRequests = maxRequests;
        this.windowDurationMillis =
                windowDuration.toMillis();
    }

    @Override
    public boolean allowRequest(String key) {

        long now = System.currentTimeMillis();

        RateLimitEntry entry =
                requestCounts.compute(
                        key,
                        (currentKey, existing) -> {

                            if (existing == null ||
                                    now - existing.windowStart()
                                            >= windowDurationMillis) {

                                return new RateLimitEntry(
                                        now,
                                        new AtomicInteger(1)
                                );
                            }

                            existing.count().incrementAndGet();

                            return existing;
                        }
                );

        return entry.count().get() <= maxRequests;
    }

    private record RateLimitEntry(
            long windowStart,
            AtomicInteger count
    ) {
    }
}
