package com.agentic.urlshortener.orchestration.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.agentic.urlshortener.orchestration.domain.RunStatus;

/** T034: the run state machine of data-model.md; terminal runs never transition (FR-RPL-05). */
@Tag("FR-ORC-11")
class RunTransitionsTest {

    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of(RunStatus.CREATED, RunStatus.RUNNING),
                Arguments.of(RunStatus.RUNNING, RunStatus.AWAITING_HUMAN),
                Arguments.of(RunStatus.RUNNING, RunStatus.PAUSED),
                Arguments.of(RunStatus.RUNNING, RunStatus.COMPENSATING),
                Arguments.of(RunStatus.RUNNING, RunStatus.COMPLETED),
                Arguments.of(RunStatus.RUNNING, RunStatus.SAFE_STOPPED),
                Arguments.of(RunStatus.AWAITING_HUMAN, RunStatus.RUNNING),
                Arguments.of(RunStatus.AWAITING_HUMAN, RunStatus.PAUSED),
                Arguments.of(RunStatus.AWAITING_HUMAN, RunStatus.COMPENSATING),
                Arguments.of(RunStatus.AWAITING_HUMAN, RunStatus.SAFE_STOPPED),
                Arguments.of(RunStatus.AWAITING_HUMAN, RunStatus.REJECTED),
                Arguments.of(RunStatus.PAUSED, RunStatus.RUNNING),
                Arguments.of(RunStatus.PAUSED, RunStatus.COMPENSATING),
                Arguments.of(RunStatus.PAUSED, RunStatus.SAFE_STOPPED),
                Arguments.of(RunStatus.COMPENSATING, RunStatus.SAFE_STOPPED),
                Arguments.of(RunStatus.COMPENSATING, RunStatus.REJECTED));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @MethodSource("allowed")
    void allowsEveryDocumentedTransition(RunStatus from, RunStatus to) {
        assertThatCode(() -> RunTransitions.check(from, to)).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortcuts() {
        assertThatThrownBy(() -> RunTransitions.check(RunStatus.CREATED, RunStatus.COMPLETED)).isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> RunTransitions.check(RunStatus.PAUSED, RunStatus.COMPLETED)).isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> RunTransitions.check(RunStatus.COMPENSATING, RunStatus.RUNNING)).isInstanceOf(IllegalTransitionException.class);
    }

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(value = RunStatus.class, names = {"COMPLETED", "REJECTED", "SAFE_STOPPED"})
    void terminalRunsNeverTransition(RunStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (RunStatus to : RunStatus.values()) {
            assertThatThrownBy(() -> RunTransitions.check(terminal, to)).isInstanceOf(IllegalTransitionException.class);
        }
    }
}
