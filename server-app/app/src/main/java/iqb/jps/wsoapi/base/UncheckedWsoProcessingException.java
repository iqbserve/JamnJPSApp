package iqb.jps.wsoapi.base;

/**
 */
public class UncheckedWsoProcessingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UncheckedWsoProcessingException(String msg) {
        super(msg);
    }

    UncheckedWsoProcessingException(String msg, Exception cause) {
        super(msg, cause);
    }
}