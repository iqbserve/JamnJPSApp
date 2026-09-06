/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <pre>
 * This implementation deliberately uses standard in and out rather than System.console.
 * The encoding contract with the server is UTF-8.
 * However, the local console may use a different charset (see resolveConsoleCharset())
 * which is converted to UTF-8 when communicating with the server.
 * </pre>
 */
public class CliConsole {

    private static final Logger LOG = LoggerFactory.getLogger(CliConsole.class);

    private static final String CLEAR_SCREEN = "\u001b[H\u001b[2J\u001b[3J";

    // ANSI: make font invisible / restore visibility - used to hide password input
    // over plain System.in when no real System.console() is attached
    private static final String CONCEAL_ON = "\u001b[8m";
    private static final String CONCEAL_OFF = "\u001b[28m";

    /**
     * <pre>
     * Determines the charset actually used by the local terminal (hopefully).
     * </pre>
     */
    private static Charset resolveConsoleCharset() {
        Console console = System.console();
        if (console != null) {
            return console.charset();
        }
        String name = System.getProperty("native.encoding");
        return name != null ? Charset.forName(name) : Charset.defaultCharset();
    }

    private boolean isOpen = false;
    private final Charset encoding;
    private BufferedReader systemIn;
    private PrintStream systemOut;
    private final ServerConnection serverConnection;
    private Supplier<String> prompt = () -> "> ";

    // guards all writes to systemOut so local prompt/messages and async
    // server responses (from the ServerConnection reader thread) never interleave
    private final Object outLock = new Object();

    public CliConsole(ServerConnection serverConnection) {
        this.encoding = resolveConsoleCharset();
        this.systemOut = System.out;
        this.serverConnection = serverConnection;
    }

    /**
     * <pre>
     * Opens the CLI console for interaction with the user.
     * </pre>
     */
    public void open() throws IOException {
        if (isOpen) {
            return;
        }
        systemIn = new BufferedReader(new InputStreamReader(System.in, encoding));
        String line;

        printLine("\nWelcome to the JPS CLI Console.");
        readPassword("Please enter password: ");
        printLine("Console ready. Type 'help' for available commands.");

        isOpen = true;
        while ((line = readNextLine()) != null) {
            line = line.trim();
            if (!processCommandLine(line)) {
                break;
            }
        }
    }

    /**
     */
    private boolean processCommandLine(String line) {
        boolean continueRunning = true;

        Command cmd = new Command(line);

        if (cmd.isCls()) {
            clearScreen();
            if (isConnected()) {
                serverConnection.send("");
            }
            return continueRunning;
        }

        if (isConnected()) {
            if (cmd.isDisconnect() || cmd.isExit()) {
                serverConnection.disconnect();
                printLine("Disconnected from the server.");
            } else {
                serverConnection.send(line);
            }
        } else {
            if (cmd.isExit()) {
                printLine("Closing console.");
                return false;
            } else if (cmd.isHelp()) {
                printLine("Available commands: cls, connect, disconnect, help, exit");
            } else if (cmd.isConnect()) {
                if (!serverConnection.connect(this::printResponseLine, this::notifyConnectionLost)) {
                    printLine("Connection failed, probably because no server is available.");
                }
            }
        }

        return continueRunning;
    }

    /**
     */
    private boolean isConnected() {
        return serverConnection != null && serverConnection.isConnected();
    }

    /**
     */
    public void close() {
        if (!isOpen) {
            return;
        }
        isOpen = false;
        if (systemIn != null) {
            try {
                systemIn.close();
            } catch (IOException e) {
                LOG.error("Failed to close system input stream", e);
            }
        }
    }

    /**
     */
    private String readNextLine() throws IOException {
        printPrompt();
        return systemIn.readLine();
    }

    /**
     * <pre>
     * Note: disabled when connected because the server sends it's own prompt.
     * Called from readNextLine() not from the printLine methods.
     * </pre>
     */
    private void printPrompt() {
        if (!isConnected()) {
            synchronized (outLock) {
                systemOut.print(prompt.get());
                systemOut.flush();
            }
        }
    }

    /**
     */
    private void clearScreen() {
        synchronized (outLock) {
            systemOut.print(CLEAR_SCREEN);
            systemOut.flush();
        }
    }

    /**
     * The local console output method.
     */
    private void printLine(String line) {
        synchronized (outLock) {
            systemOut.println(line);
            systemOut.flush();
        }
    }

    /**
     * <pre>
     * The remote console output method used by the ServerConnection.
     * </pre>
     */
    private void printResponseLine(String line) {
        synchronized (outLock) {
            systemOut.print(line);
            systemOut.flush();
        }
    }

    /**
     * Called from the ServerConnection when the connection drops unexpectedly (not
     * via a local disconnect/exit).
     */
    private void notifyConnectionLost() {
        printLine("Connection to server lost.");
        printPrompt();
    }

    /**
     * <pre>
     * Reading a password without echoing it to the screen.
     * !!! IMPORTANT HINT: This is a SECURITY SENSITIVE function - with LIMITED security !!!
     * 
     * If available the function uses the build in System.console().readPassword().
     * 
     * If System.consel() is NOT available, 
     * the function falls back to hiding the typed text via ANSI "invisible font" over plain System.in.
     *
     * The fallback is cosmetic only - the password characters are present - they are merely rendered invisible.
     * 
     * </pre>
     */
    public PasswordWrapper readPassword(String promptText) throws IOException {
        Console console = System.console();
        if (console != null) {
            return new PasswordWrapper(console.readPassword("%s", promptText));
        }

        synchronized (outLock) {
            systemOut.print(promptText);
            systemOut.print(CONCEAL_ON);
            systemOut.flush();
        }
        try {
            String line = systemIn.readLine();
            return line != null ? new PasswordWrapper(line.toCharArray()) : new PasswordWrapper(new char[0]);
        } finally {
            synchronized (outLock) {
                systemOut.print(CONCEAL_OFF);
                systemOut.flush();
            }
        }
    }

    /**
     * Represents a command entered by the user.
     */
    protected static class Command {

        private final String token;

        public Command(String token) {
            this.token = token;
        }

        public boolean isCls() {
            return token.equalsIgnoreCase("cls");
        }

        public boolean isExit() {
            return token.equalsIgnoreCase("exit");
        }

        public boolean isConnect() {
            return token.equalsIgnoreCase("connect");
        }

        public boolean isDisconnect() {
            return token.equalsIgnoreCase("disconnect");
        }

        public boolean isHelp() {
            return token.equalsIgnoreCase("help");
        }
    }

    /**
     * A quite safe password for a quite unsafe environment ;-).
     */
    protected static final class PasswordWrapper implements AutoCloseable {
        private final AtomicReference<char[]> valueRef;

        public PasswordWrapper(char[] input) {
            this.valueRef = new AtomicReference<>(input.clone());
            Arrays.fill(input, '\0');
        }

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

        @Override
        public void close() {
            char[] chars = valueRef.getAndSet(null);
            if (chars != null) {
                Arrays.fill(chars, '\0');
            }
        }
    }
}
