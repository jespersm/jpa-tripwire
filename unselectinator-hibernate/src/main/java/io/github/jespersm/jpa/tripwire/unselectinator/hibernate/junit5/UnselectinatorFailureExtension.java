package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.junit5;

import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.CollectionLoadGuard;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;

/**
 * Reports always-on Unselectinator threshold violations as test failures.
 */
public final class UnselectinatorFailureExtension
        implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        handle(throwable);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handle(throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handle(throwable);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handle(throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handle(throwable);
    }

    private static void handle(Throwable throwable) throws Throwable {
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof CollectionLoadGuard.ThresholdExceededException) {
                throw new AssertionFailedError(cause.getMessage(), cause);
            }
        }
        throw throwable;
    }
}
