package iqb.jps.core;

/**
 */
public class UncheckedJsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UncheckedJsonException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public UncheckedJsonException(String msg) {
        super(msg);
    }
}