package com.strataresolve.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ticket module.
 * Maps to the {@code app.ticket} namespace in application.yml.
 */
@ConfigurationProperties(prefix = "app.ticket")
public record TicketProperties(
        /**
         * Number of hours after closure/resolution within which a ticket can be reopened.
         * Defaults to 72 hours (3 days) if not configured.
         */
        int reopenWindowHours,

        /**
         * Configuration for duplicate detection.
         */
        DuplicateDetection duplicateDetection,

        /**
         * Rate limiting configuration for ticket submissions.
         */
        RateLimitProperties rateLimit
) {
    public TicketProperties {
        if (reopenWindowHours <= 0) {
            reopenWindowHours = 72;
        }
        if (duplicateDetection == null) {
            duplicateDetection = new DuplicateDetection(48, 0.6);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimitProperties(10, 60);
        }
    }

    /**
     * Configuration properties for duplicate ticket detection.
     */
    public record DuplicateDetection(
            /**
             * Number of hours to look back when checking for potential duplicates.
             * Defaults to 48 hours (2 days).
             */
            int timeWindowHours,

            /**
             * Minimum similarity threshold (0.0 to 1.0) for title matching.
             * A value of 0.6 means titles must be at least 60% similar to be considered duplicates.
             * Defaults to 0.6.
             */
            double similarityThreshold
    ) {
        public DuplicateDetection {
            if (timeWindowHours <= 0) {
                timeWindowHours = 48;
            }
            if (similarityThreshold <= 0.0 || similarityThreshold > 1.0) {
                similarityThreshold = 0.6;
            }
        }
    }

    /**
     * Rate limiting properties for ticket submissions.
     *
     * @param maxSubmissionsPerPeriod Maximum number of ticket submissions allowed per resident per time period.
     *                                Defaults to 10.
     * @param periodMinutes           The time period duration in minutes. Defaults to 60 minutes (1 hour).
     */
    public record RateLimitProperties(
            int maxSubmissionsPerPeriod,
            int periodMinutes
    ) {
        public RateLimitProperties {
            if (maxSubmissionsPerPeriod <= 0) {
                maxSubmissionsPerPeriod = 10;
            }
            if (periodMinutes <= 0) {
                periodMinutes = 60;
            }
        }
    }
}
