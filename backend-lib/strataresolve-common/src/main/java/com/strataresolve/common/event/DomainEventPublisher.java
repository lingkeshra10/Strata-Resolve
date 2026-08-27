package com.strataresolve.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Wrapper service around Spring's {@link ApplicationEventPublisher}
 * for publishing domain events across modules.
 *
 * <p>Provides a single point of entry for event publication with
 * logging for observability. All events are dispatched synchronously
 * within the current transaction boundary.</p>
 */
@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes a domain event to all registered listeners.
     *
     * @param event the domain event to publish
     */
    public void publish(DomainEvent event) {
        log.debug("Publishing domain event: {} [actor={}, property={}]",
                event.getClass().getSimpleName(),
                event.getActingUserId(),
                event.getPropertyId());
        applicationEventPublisher.publishEvent(event);
    }
}
