package com.strataresolve.vendor.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStatusTest {

    @Test
    void created_canTransitionToAccepted() {
        assertThat(WorkOrderStatus.CREATED.canTransitionTo(WorkOrderStatus.ACCEPTED)).isTrue();
    }

    @Test
    void created_canTransitionToCancelled() {
        assertThat(WorkOrderStatus.CREATED.canTransitionTo(WorkOrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void created_cannotTransitionToInProgress() {
        assertThat(WorkOrderStatus.CREATED.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void created_cannotTransitionToCompleted() {
        assertThat(WorkOrderStatus.CREATED.canTransitionTo(WorkOrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void accepted_canTransitionToInProgress() {
        assertThat(WorkOrderStatus.ACCEPTED.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void accepted_canTransitionToCancelled() {
        assertThat(WorkOrderStatus.ACCEPTED.canTransitionTo(WorkOrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void accepted_cannotTransitionToCompleted() {
        assertThat(WorkOrderStatus.ACCEPTED.canTransitionTo(WorkOrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void inProgress_canTransitionToCompleted() {
        assertThat(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void inProgress_canTransitionToCancelled() {
        assertThat(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void inProgress_cannotTransitionToAccepted() {
        assertThat(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.ACCEPTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void completed_cannotTransitionToAnyStatus(WorkOrderStatus target) {
        assertThat(WorkOrderStatus.COMPLETED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void cancelled_cannotTransitionToAnyStatus(WorkOrderStatus target) {
        assertThat(WorkOrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }

    @Test
    void noStatus_canTransitionToCreated() {
        // CREATED is the initial state, no status should be able to transition to it
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            assertThat(status.canTransitionTo(WorkOrderStatus.CREATED)).isFalse();
        }
    }
}
