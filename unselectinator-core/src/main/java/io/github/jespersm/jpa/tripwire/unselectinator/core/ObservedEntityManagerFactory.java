package io.github.jespersm.jpa.tripwire.unselectinator.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
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

    /**
     * Wrap an {@link EntityManager} so explicit fetch operations are tracked.
     *
     * @param delegate original entity manager
     * @param tracker tracker receiving explicit fetch lifecycle callbacks
     * @return proxied entity manager
     */
    public static EntityManager wrap(EntityManager delegate, EntityLoadTracker tracker) {
        return wrap(delegate, tracker, EntityManager.class);
    }

    /**
     * Wrap an EntityManager while preserving provider or framework interfaces.
     *
     * @param delegate original entity manager
     * @param tracker tracker receiving explicit fetch lifecycle callbacks
     * @param interfaces interfaces exposed by the proxy, including EntityManager or a subinterface
     * @return proxied entity manager
     */
    public static EntityManager wrap(EntityManager delegate, EntityLoadTracker tracker, Class<?>... interfaces) {
        InvocationHandler handler = new ObservedEntityManagerInvocationHandler(delegate, tracker);
        return (EntityManager) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                interfaces,
                handler
        );
    }

    private record ObservedEntityManagerInvocationHandler(EntityManager delegate,
                                                          EntityLoadTracker tracker) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeDelegate(method, delegate, args);
            }

            String methodName = method.getName();
            if (QUERY_CREATION_METHODS.contains(methodName)) {
                Object result = invokeDelegate(method, delegate, args);
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
                    return invokeDelegate(method, delegate, args);
                } finally {
                    tracker.endExplicitFetch();
                }
            }

            return invokeDelegate(method, delegate, args);
        }
    }

    private static Object invokeDelegate(Method method, Object delegate, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private static Object wrapQuery(Query delegate,
                                    EntityLoadTracker tracker,
                                    FetchEndpoint endpoint,
                                    Class<?> declaredReturnType) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeDelegate(method, delegate, args);
            }
            // Spring Data uses unwrap(null) to retrieve the target of a JDK query proxy.
            if (method.getName().equals("unwrap") && args != null && args.length == 1 && args[0] == null) {
                return delegate;
            }

            Object result;
            if (QUERY_EXECUTION_METHODS.contains(method.getName())) {
                tracker.beginExplicitFetch(endpoint);
                try {
                    result = invokeDelegate(method, delegate, args);
                } finally {
                    tracker.endExplicitFetch();
                }
            } else {
                result = invokeDelegate(method, delegate, args);
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
