package com.agentic.urlshortener.orchestration.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import com.agentic.urlshortener.orchestration.agent.AgentPermission;
import com.agentic.urlshortener.orchestration.agent.AgentPermissionDeniedException;
import com.agentic.urlshortener.orchestration.agent.PermanentStageException;
import com.agentic.urlshortener.orchestration.agent.TransientStageException;
import com.agentic.urlshortener.orchestration.domain.FailureClass;

/** T067 (FR-REL-02, plan §6 failure taxonomy): transient failures are retried, everything else is permanent. */
@Tag("FR-REL-02")
class FailureClassifierTest {

    private final FailureClassifier classifier = new FailureClassifier();

    @Test
    void transientCausesAreClassifiedTransient() {
        assertThat(classifier.classify(new TransientStageException("store busy"))).isEqualTo(FailureClass.TRANSIENT);
        assertThat(classifier.classify(new TransientDataAccessResourceException("pool exhausted"))).isEqualTo(FailureClass.TRANSIENT);
        assertThat(classifier.classify(new CannotAcquireLockException("lock timeout"))).isEqualTo(FailureClass.TRANSIENT);
        assertThat(classifier.classify(new QueryTimeoutException("slow query"))).isEqualTo(FailureClass.TRANSIENT);
    }

    @Test
    void everythingElseIsPermanentByDefault() {
        assertThat(classifier.classify(new PermanentStageException("invalid design"))).isEqualTo(FailureClass.PERMANENT);
        assertThat(classifier.classify(new IllegalArgumentException("bad input"))).isEqualTo(FailureClass.PERMANENT);
        assertThat(classifier.classify(new NullPointerException())).isEqualTo(FailureClass.PERMANENT);
        assertThat(classifier.classify(new AgentPermissionDeniedException("agent@1", AgentPermission.READ_LINKS, "findLink")))
                .isEqualTo(FailureClass.PERMANENT);
    }

    @Test
    void aTransientCauseWrappedInAnotherExceptionIsStillTransient() {
        assertThat(classifier.classify(new IllegalStateException("wrapped", new CannotAcquireLockException("lock"))))
                .isEqualTo(FailureClass.TRANSIENT);
    }
}
