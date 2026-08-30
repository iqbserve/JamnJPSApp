/* Authored by iqbserve.de */
package iqb.jps.core;

import java.util.function.Supplier;

/**
 * The WebServiceRegistry interface defines a contract for registering web services within the application.
 * Implementations of this interface should provide the mechanism for registering services using a supplier.
 */
public interface WebServiceRegistry {

    public WebServiceRegistry registerServices(Supplier<Object> serviceSupplier);
}
