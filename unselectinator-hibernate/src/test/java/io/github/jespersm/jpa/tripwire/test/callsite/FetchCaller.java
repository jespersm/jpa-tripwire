package io.github.jespersm.jpa.tripwire.test.callsite;

import io.github.jespersm.jpa.tripwire.test.model.fixture.Customer;
import io.github.jespersm.jpa.tripwire.test.model.fixture.Order;
import jakarta.persistence.EntityManager;

import java.util.function.Supplier;

/** Application frames outside the instrumentation package, used to verify caller capture. */
public final class FetchCaller {
    private FetchCaller() {
    }

    public static Customer loadCustomer(EntityManager entityManager, Long id) {
        return entityManager.find(Customer.class, id);
    }

    public static void initializeOrders(Customer customer) {
        customer.getOrders().size();
    }

    public static String shippingCarrier(Order order) {
        return order.getShippingInfo().getCarrier();
    }

    public static <T> T access(Supplier<T> association) {
        return association.get();
    }
}
