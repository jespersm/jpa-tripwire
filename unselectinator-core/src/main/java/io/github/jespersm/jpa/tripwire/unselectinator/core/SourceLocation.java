package io.github.jespersm.jpa.tripwire.unselectinator.core;

/**
 * Caller location captured from the current stack.
 *
 * @param className declaring class name
 * @param methodName method name
 * @param fileName source file name
 * @param lineNumber source line number
 */
public record SourceLocation(String className, String methodName, String fileName, int lineNumber) {

    /** Capture the current application caller, excluding persistence and proxy infrastructure. */
    public static SourceLocation capture() {
        return StackFrameCapture.captureUserLocation();
    }

    @Override
    public String toString() {
        return className + "#" + methodName + "(" + fileName + ":" + lineNumber + ")";
    }
}

