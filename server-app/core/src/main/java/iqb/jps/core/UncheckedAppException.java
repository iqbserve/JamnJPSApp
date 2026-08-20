/* Authored by iqbserve.de */
package iqb.jps.core;

/**
 */
public class UncheckedAppException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UncheckedAppException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public UncheckedAppException(String msg) {
        super(msg);
    }
}