package iqb.jps.core;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A quite safe password for a quite unsafe environment ;-).
 */
public final class PasswordObject implements AutoCloseable {
    private final AtomicReference<char[]> valueRef;

    /**
     * Creates a new PasswordObject by cloning the input array and clearing the original.
     */
    public PasswordObject(char[] input) {
        this.valueRef = new AtomicReference<>(input.clone());
        Arrays.fill(input, '\0');
    }

    /**
     * Applies the password to the given consumer exactly once, then clears it.
     */
    public void oneTimeApplyTo(Consumer<char[]> consumer) {
        char[] chars = valueRef.get();
        if (chars == null) {
            throw new IllegalStateException("Password has already been cleared.");
        }
        try {
            consumer.accept(chars);
        } finally {
            close();
        }
    }

    /**
     * Clears the password from memory.
     */
    @Override
    public void close() {
        char[] chars = valueRef.getAndSet(null);
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }
}