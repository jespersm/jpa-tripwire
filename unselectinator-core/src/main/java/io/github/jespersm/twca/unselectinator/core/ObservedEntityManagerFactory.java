package io.github.jespersm.twca.unselectinator.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * Creates JPA proxies that mark explicit EntityManager fetch endpoints.
 */
public final class ObservedEntityManagerFactory {
    private static final Set<String> QUERY_CREATION_METHODS = Set.of(
            "createQuery",
            "createNamedQuery",
            "createNativeQuery"
    );
    private static final Set<String> QUERY_EXECUTION_METHODS = Set.of(
            "getResultList",
            "getSingleResult",
            "getResultStream",
            "executeUpdate"
    );
    private static final Set<String> IMMEDIATE_FETCH_METHODS = Set.of(
            "find",
            "refresh"
    );

    private ObservedEntityManagerFactory() {
    }

    public static EntityManager wrap(EntityManager delegate, EntityLoadTracker tracker) {
        InvocationHandler handler = new ObservedEntityManagerInvocationHandler(delegate, tracker);
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                handler
        );
    }

    private record ObservedEntityManagerInvocationHandler(EntityManager delegate,
                                                          EntityLoadTracker tracker) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }

            String methodName = method.getName();
            if (QUERY_CREATION_METHODS.contains(methodName)) {
                Object result = method.invoke(delegate, args);
                if (result instanceof Query query) {
                    FetchEndpoint endpoint = FetchEndpoints.entityManagerMethod(methodName);
                    return wrapQuery(query, tracker, endpoint, method.getReturnType());
                }
                return result;
            }

            if (IMMEDIATE_FETCH_METHODS.contains(methodName)) {
                FetchEndpoint endpoint = FetchEndpoints.entityManagerMethod(methodName);
                tracker.beginExplicitFetch(endpoint);
                try {
                    return method.invoke(delegate, args);
                } finally {
                    tracker.endExplicitFetch();
                }
            }

            return method.invoke(delegate, args);
        }
    }

    private static Object wrapQuery(Query delegate,
                                    EntityLoadTracker tracker,
                                    FetchEndpoint endpoint,
                                    Class<?> declaredReturnType) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }

            Object result;
            if (QUERY_EXECUTION_METHODS.contains(method.getName())) {
                tracker.beginExplicitFetch(endpoint);
                try {
                    result = method.invoke(delegate, args);
                } finally {
                    tracker.endExplicitFetch();
                }
            } else {
                result = method.invoke(delegate, args);
            }

            if (result == delegate) {
                return proxy;
            }
            return result;
        };

        Class<?> proxyType = declaredReturnType.isInterface() ? declaredReturnType : Query.class;
        return Proxy.newProxyInstance(
                proxyType.getClassLoader(),
                new Class<?>[]{proxyType},
                handler
        );
    }
}


