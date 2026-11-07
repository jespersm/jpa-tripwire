package io.github.jespersm.jpa.tripwire.unselectinator.core;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class StackFrameCapture {
    private static final SourceLocation UNKNOWN = new SourceLocation("<unknown>", "<unknown>", "<unknown>", -1);
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    // ClassValue releases the per-class call-site cache when its class loader is collected.
    private static final ClassValue<ConcurrentMap<SourceLocation, SourceLocation>> LOCATIONS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<SourceLocation, SourceLocation> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final List<String> INFRASTRUCTURE_PREFIXES = List.of(
            "io.github.jespersm.jpa.tripwire.unselectinator.",
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
                .map(frame -> {
                    var location = new SourceLocation(frame.getClassName(), frame.getMethodName(),
                            frame.getFileName(), frame.getLineNumber());
                    return LOCATIONS.get(frame.getDeclaringClass()).computeIfAbsent(location, key -> key);
                })
                .orElse(UNKNOWN));
    }

    private static boolean isRelevant(StackFrame frame) {
        String className = frame.getClassName();
        return !className.contains("$HibernateProxy") && !className.contains("$$SpringCGLIB$$")
                && INFRASTRUCTURE_PREFIXES.stream().noneMatch(className::startsWith);
    }
}

