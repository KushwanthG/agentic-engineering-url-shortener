package com.agentic.urlshortener.orchestration.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.agentic.urlshortener.orchestration.domain.StageStatus;

/** T034: the stage state machine of data-model.md — every allowed transition passes, prohibited ones throw. */
@Tag("FR-ORC-11")
class StageTransitionsTest {

    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of(StageStatus.PENDING, StageStatus.READY),
                Arguments.of(StageStatus.PENDING, StageStatus.SKIPPED),
                Arguments.of(StageStatus.PENDING, StageStatus.CANCELLED),
                Arguments.of(StageStatus.PENDING, StageStatus.REMOVED),
                Arguments.of(StageStatus.PENDING, StageStatus.FAILED),
                Arguments.of(StageStatus.READY, StageStatus.RUNNING),
                Arguments.of(StageStatus.READY, StageStatus.SUCCEEDED),
                Arguments.of(StageStatus.READY, StageStatus.AWAITING_DECISION),
                Arguments.of(StageStatus.READY, StageStatus.CANCELLED),
                Arguments.of(StageStatus.RUNNING, StageStatus.SUCCEEDED),
                Arguments.of(StageStatus.RUNNING, StageStatus.RETRY_WAIT),
                Arguments.of(StageStatus.RUNNING, StageStatus.RUNNING),
                Arguments.of(StageStatus.RUNNING, StageStatus.FAILED),
                Arguments.of(StageStatus.RUNNING, StageStatus.AWAITING_DECISION),
                Arguments.of(StageStatus.RUNNING, StageStatus.PENDING),
                Arguments.of(StageStatus.RUNNING, StageStatus.CANCELLED),
                Arguments.of(StageStatus.RETRY_WAIT, StageStatus.RUNNING),
                Arguments.of(StageStatus.RETRY_WAIT, StageStatus.PENDING),
                Arguments.of(StageStatus.RETRY_WAIT, StageStatus.CANCELLED),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.SUCCEEDED),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.READY),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.FAILED),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.PENDING),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.CANCELLED),
                Arguments.of(StageStatus.SUCCEEDED, StageStatus.PENDING),
                Arguments.of(StageStatus.SUCCEEDED, StageStatus.COMPENSATED),
                Arguments.of(StageStatus.SKIPPED, StageStatus.PENDING));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @MethodSource("allowed")
    void allowsEveryDocumentedTransition(StageStatus from, StageStatus to) {
        assertThatCode(() -> StageTransitions.check(from, to)).doesNotThrowAnyException();
    }

    static Stream<Arguments> prohibited() {
        return Stream.of(
                Arguments.of(StageStatus.PENDING, StageStatus.SUCCEEDED),
                Arguments.of(StageStatus.PENDING, StageStatus.RUNNING),
                Arguments.of(StageStatus.SUCCEEDED, StageStatus.RUNNING),
                Arguments.of(StageStatus.AWAITING_DECISION, StageStatus.RUNNING),
                Arguments.of(StageStatus.SKIPPED, StageStatus.SUCCEEDED));
    }

    @ParameterizedTest(name = "{0} -> {1} prohibited")
    @MethodSource("prohibited")
    void rejectsProhibitedExamples(StageStatus from, StageStatus to) {
        assertThatThrownBy(() -> StageTransitions.check(from, to))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining(from.name()).hasMessageContaining(to.name());
    }

    @ParameterizedTest(name = "nothing leaves {0}")
    @EnumSource(value = StageStatus.class, names = {"FAILED", "COMPENSATED", "CANCELLED", "REMOVED"})
    void terminalNodeStatesHaveNoExit(StageStatus terminal) {
        for (StageStatus to : StageStatus.values()) {
            assertThatThrownBy(() -> StageTransitions.check(terminal, to)).isInstanceOf(IllegalTransitionException.class);
        }
    }

    @Test
    void terminalFlagMatchesTheTable() {
        org.assertj.core.api.Assertions.assertThat(StageStatus.FAILED.isTerminal()).isTrue();
        org.assertj.core.api.Assertions.assertThat(StageStatus.SUCCEEDED.isTerminal()).isFalse();
    }
}
