package com.strataresolve.ticket.policy;

import com.strataresolve.shared.exception.InvalidTransitionException;
import com.strataresolve.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusWorkflowEngineTest {

    private StatusWorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StatusWorkflowEngine();
    }

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        static Stream<Arguments> validTransitionPairs() {
            return Stream.of(
                    // From SUBMITTED
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.ACKNOWLEDGED),
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.REJECTED),
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.CANCELLED),
                    // From ACKNOWLEDGED
                    Arguments.of(TicketStatus.ACKNOWLEDGED, TicketStatus.UNDER_REVIEW),
                    Arguments.of(TicketStatus.ACKNOWLEDGED, TicketStatus.ASSIGNED),
                    Arguments.of(TicketStatus.ACKNOWLEDGED, TicketStatus.REJECTED),
                    Arguments.of(TicketStatus.ACKNOWLEDGED, TicketStatus.CANCELLED),
                    // From UNDER_REVIEW
                    Arguments.of(TicketStatus.UNDER_REVIEW, TicketStatus.ASSIGNED),
                    Arguments.of(TicketStatus.UNDER_REVIEW, TicketStatus.REJECTED),
                    Arguments.of(TicketStatus.UNDER_REVIEW, TicketStatus.CANCELLED),
                    // From ASSIGNED
                    Arguments.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS),
                    Arguments.of(TicketStatus.ASSIGNED, TicketStatus.AWAITING_VENDOR),
                    Arguments.of(TicketStatus.ASSIGNED, TicketStatus.CANCELLED),
                    // From IN_PROGRESS
                    Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_VENDOR),
                    Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_RESIDENT),
                    Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.READY_FOR_VERIFICATION),
                    Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
                    // From AWAITING_VENDOR
                    Arguments.of(TicketStatus.AWAITING_VENDOR, TicketStatus.IN_PROGRESS),
                    Arguments.of(TicketStatus.AWAITING_VENDOR, TicketStatus.CANCELLED),
                    // From AWAITING_RESIDENT
                    Arguments.of(TicketStatus.AWAITING_RESIDENT, TicketStatus.IN_PROGRESS),
                    Arguments.of(TicketStatus.AWAITING_RESIDENT, TicketStatus.CANCELLED),
                    // From READY_FOR_VERIFICATION
                    Arguments.of(TicketStatus.READY_FOR_VERIFICATION, TicketStatus.RESOLVED),
                    Arguments.of(TicketStatus.READY_FOR_VERIFICATION, TicketStatus.IN_PROGRESS),
                    // From RESOLVED
                    Arguments.of(TicketStatus.RESOLVED, TicketStatus.CLOSED),
                    Arguments.of(TicketStatus.RESOLVED, TicketStatus.REOPENED),
                    // From CLOSED
                    Arguments.of(TicketStatus.CLOSED, TicketStatus.REOPENED),
                    // From REOPENED
                    Arguments.of(TicketStatus.REOPENED, TicketStatus.ACKNOWLEDGED),
                    Arguments.of(TicketStatus.REOPENED, TicketStatus.ASSIGNED),
                    Arguments.of(TicketStatus.REOPENED, TicketStatus.CANCELLED)
            );
        }

        @ParameterizedTest(name = "{0} -> {1} should be allowed")
        @MethodSource("validTransitionPairs")
        void shouldAllowValidTransitions(TicketStatus from, TicketStatus to) {
            // Should not throw
            engine.validateTransition(from, to);
            assertThat(engine.isTransitionAllowed(from, to)).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        static Stream<Arguments> invalidTransitionPairs() {
            return Stream.of(
                    // SUBMITTED cannot go directly to IN_PROGRESS
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.IN_PROGRESS),
                    // SUBMITTED cannot go to RESOLVED
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.RESOLVED),
                    // SUBMITTED cannot go to CLOSED
                    Arguments.of(TicketStatus.SUBMITTED, TicketStatus.CLOSED),
                    // ASSIGNED cannot go to RESOLVED directly
                    Arguments.of(TicketStatus.ASSIGNED, TicketStatus.RESOLVED),
                    // IN_PROGRESS cannot go back to SUBMITTED
                    Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.SUBMITTED),
                    // RESOLVED cannot go to IN_PROGRESS
                    Arguments.of(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS),
                    // CLOSED cannot go to RESOLVED
                    Arguments.of(TicketStatus.CLOSED, TicketStatus.RESOLVED)
            );
        }

        @ParameterizedTest(name = "{0} -> {1} should be rejected")
        @MethodSource("invalidTransitionPairs")
        void shouldRejectInvalidTransitions(TicketStatus from, TicketStatus to) {
            assertThatThrownBy(() -> engine.validateTransition(from, to))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining(from.name())
                    .hasMessageContaining(to.name());
            assertThat(engine.isTransitionAllowed(from, to)).isFalse();
        }
    }

    @Nested
    @DisplayName("Terminal statuses")
    class TerminalStatuses {

        @ParameterizedTest(name = "{0} should not allow any transition")
        @EnumSource(value = TicketStatus.class, names = {"REJECTED", "CANCELLED"})
        void terminalStatusShouldRejectAllTransitions(TicketStatus terminalStatus) {
            for (TicketStatus target : TicketStatus.values()) {
                if (target == terminalStatus) continue;
                assertThat(engine.isTransitionAllowed(terminalStatus, target)).isFalse();
                assertThatThrownBy(() -> engine.validateTransition(terminalStatus, target))
                        .isInstanceOf(InvalidTransitionException.class);
            }
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            assertThat(engine.isTerminal(TicketStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelledIsTerminal() {
            assertThat(engine.isTerminal(TicketStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("SUBMITTED is not terminal")
        void submittedIsNotTerminal() {
            assertThat(engine.isTerminal(TicketStatus.SUBMITTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should reject null current status")
        void shouldRejectNullCurrentStatus() {
            assertThatThrownBy(() -> engine.validateTransition(null, TicketStatus.ACKNOWLEDGED))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should reject null target status")
        void shouldRejectNullTargetStatus() {
            assertThatThrownBy(() -> engine.validateTransition(TicketStatus.SUBMITTED, null))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should reject same-status transition")
        void shouldRejectSameStatusTransition() {
            assertThatThrownBy(() -> engine.validateTransition(TicketStatus.SUBMITTED, TicketStatus.SUBMITTED))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("already in status");
        }

        @Test
        @DisplayName("isTransitionAllowed returns false for null current status")
        void isTransitionAllowedReturnsFalseForNullCurrent() {
            assertThat(engine.isTransitionAllowed(null, TicketStatus.ACKNOWLEDGED)).isFalse();
        }

        @Test
        @DisplayName("isTransitionAllowed returns false for null target status")
        void isTransitionAllowedReturnsFalseForNullTarget() {
            assertThat(engine.isTransitionAllowed(TicketStatus.SUBMITTED, null)).isFalse();
        }

        @Test
        @DisplayName("isTransitionAllowed returns false for same status")
        void isTransitionAllowedReturnsFalseForSameStatus() {
            assertThat(engine.isTransitionAllowed(TicketStatus.SUBMITTED, TicketStatus.SUBMITTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("getAllowedTransitions")
    class GetAllowedTransitions {

        @Test
        @DisplayName("SUBMITTED allows ACKNOWLEDGED, REJECTED, CANCELLED")
        void submittedAllowedTransitions() {
            Set<TicketStatus> allowed = engine.getAllowedTransitions(TicketStatus.SUBMITTED);
            assertThat(allowed).containsExactlyInAnyOrder(
                    TicketStatus.ACKNOWLEDGED, TicketStatus.REJECTED, TicketStatus.CANCELLED);
        }

        @Test
        @DisplayName("IN_PROGRESS allows AWAITING_VENDOR, AWAITING_RESIDENT, READY_FOR_VERIFICATION, CANCELLED")
        void inProgressAllowedTransitions() {
            Set<TicketStatus> allowed = engine.getAllowedTransitions(TicketStatus.IN_PROGRESS);
            assertThat(allowed).containsExactlyInAnyOrder(
                    TicketStatus.AWAITING_VENDOR, TicketStatus.AWAITING_RESIDENT,
                    TicketStatus.READY_FOR_VERIFICATION, TicketStatus.CANCELLED);
        }

        @Test
        @DisplayName("REJECTED has no allowed transitions")
        void rejectedHasNoAllowedTransitions() {
            Set<TicketStatus> allowed = engine.getAllowedTransitions(TicketStatus.REJECTED);
            assertThat(allowed).isEmpty();
        }

        @Test
        @DisplayName("CANCELLED has no allowed transitions")
        void cancelledHasNoAllowedTransitions() {
            Set<TicketStatus> allowed = engine.getAllowedTransitions(TicketStatus.CANCELLED);
            assertThat(allowed).isEmpty();
        }

        @Test
        @DisplayName("null status returns empty set")
        void nullStatusReturnsEmptySet() {
            Set<TicketStatus> allowed = engine.getAllowedTransitions(null);
            assertThat(allowed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Error message quality")
    class ErrorMessages {

        @Test
        @DisplayName("error message for invalid transition includes both statuses")
        void errorMessageIncludesBothStatuses() {
            assertThatThrownBy(() -> engine.validateTransition(TicketStatus.SUBMITTED, TicketStatus.CLOSED))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("SUBMITTED")
                    .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("error message for terminal status transition includes statuses")
        void errorMessageForTerminalTransition() {
            assertThatThrownBy(() -> engine.validateTransition(TicketStatus.CANCELLED, TicketStatus.SUBMITTED))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("CANCELLED")
                    .hasMessageContaining("SUBMITTED");
        }
    }
}
