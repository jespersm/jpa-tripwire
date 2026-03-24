package io.github.jespersm.twca.test.unselectinator;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.twca.unselectinator.core.FetchEndpoint;
import io.github.jespersm.twca.unselectinator.core.FetchEndpoints;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Treat repository method invocations as the user-visible explicit fetch boundary.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RepositoryFetchObservationAspect {
    private final EntityLoadTracker tracker;

    public RepositoryFetchObservationAspect(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    @Around("this(org.springframework.data.repository.Repository) && execution(public * *(..))")
    public Object observeRepositoryFetch(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (method.getDeclaringClass() == Object.class) {
            return joinPoint.proceed();
        }

        FetchEndpoint endpoint = FetchEndpoints.repositoryMethod(method.getDeclaringClass(), method.getName());
        tracker.beginExplicitFetch(endpoint);
        try {
            return joinPoint.proceed();
        } finally {
            tracker.endExplicitFetch();
        }
    }
}

