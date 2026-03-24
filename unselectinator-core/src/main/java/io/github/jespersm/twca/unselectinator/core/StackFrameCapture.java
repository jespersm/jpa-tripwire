package io.github.jespersm.twca.unselectinator.core;

import java.lang.StackWalker.StackFrame;
import java.util.List;

final class StackFrameCapture {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final List<String> INFRASTRUCTURE_PREFIXES = List.of(
            "io.github.jespersm.unselectinator.",
            "org.springframework.",
            "org.hibernate.",
            "jakarta.persistence.",
            "java.",
            "javax.",
            "jdk.",
            "sun."
    );

    private StackFrameCapture() {
    }

    static SourceLocation captureUserLocation() {
        return STACK_WALKER.walk(stream -> stream
                .filter(StackFrameCapture::isRelevant)
                .findFirst()
                .map(frame -> new SourceLocation(
                        frame.getClassName(),
                        frame.getMethodName(),
                        frame.getFileName(),
                        frame.getLineNumber()
                ))
                .orElse(new SourceLocation("<unknown>", "<unknown>", "<unknown>", -1)));
    }

    private static boolean isRelevant(StackFrame frame) {
        String className = frame.getClassName();
        return INFRASTRUCTURE_PREFIXES.stream().noneMatch(className::startsWith);
    }
}

