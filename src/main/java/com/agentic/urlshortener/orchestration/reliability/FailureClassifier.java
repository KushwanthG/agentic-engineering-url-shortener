package com.agentic.urlshortener.orchestration.reliability;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.TransientStageException;
import com.agentic.urlshortener.orchestration.domain.FailureClass;

/**
 * Failure taxonomy of plan.md §6: an exception is {@code TRANSIENT} when it, or any cause, is a
 * {@link TransientStageException} or a {@link TransientDataAccessException} (lock acquisition, query
 * timeout, exhausted pool); everything else, including permission denials and invalid input, is
 * {@code PERMANENT}. Timeouts are classified by the dispatcher.
 */
@Component
public class FailureClassifier {

    public FailureClass classify(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TransientStageException || t instanceof TransientDataAccessException) {
                return FailureClass.TRANSIENT;
            }
        }
        return FailureClass.PERMANENT;
    }
}
