package com.strataresolve.ticket.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the ticket module.
 * Enables binding of {@link TicketProperties} from application.yml.
 */
@Configuration
@EnableConfigurationProperties(TicketProperties.class)
public class TicketModuleConfig {
}
