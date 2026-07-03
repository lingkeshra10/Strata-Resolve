/**
 * Domain event infrastructure for intra-module communication.
 *
 * <p>Uses Spring's {@link org.springframework.context.ApplicationEventPublisher}
 * for synchronous event dispatch within the current transaction boundary.
 * Modules publish events via {@link com.strataresolve.shared.event.DomainEventPublisher}
 * and subscribe using {@link org.springframework.context.event.EventListener} or
 * {@link org.springframework.transaction.event.TransactionalEventListener}.</p>
 */
package com.strataresolve.shared.event;
