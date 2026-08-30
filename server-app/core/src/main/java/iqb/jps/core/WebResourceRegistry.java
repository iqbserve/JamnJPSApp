/* Authored by iqbserve.de */
package iqb.jps.core;


/**
 * The WebResourceRegistry interface defines a contract for registering web resources from within the application.
 */
public interface WebResourceRegistry {

    /**
     */ 
    public WebResourceRegistry registerResource(String path, byte[] data);
}
