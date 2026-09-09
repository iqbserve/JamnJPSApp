/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.cli.CliCommand;
import iqb.jps.cli.CliCommandLine;
import iqb.jps.cli.CliCommandRegistry;
import iqb.jps.core.PasswordObject;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
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
    private final CliCommandRegistry<CmdCallContext> commandRegistry;

    // guards all writes to systemOut so local prompt/messages and async
    // server responses (from the ServerConnection reader thread) never interleave
    private final Object outLock = new Object();

    public CliConsole(ServerConnection serverConnection, CliCommandRegistry<CmdCallContext> commandRegistry) {
        this.encoding = resolveConsoleCharset();
        this.systemOut = System.out;
        this.commandRegistry = commandRegistry;
        this.serverConnection = serverConnection
                .setServerOutputConsumer(this::printResponseLine)
                .setOnConnectionLost(this::notifyConnectionLost);
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

        CmdCallContext ctx = new CmdCallContext(serverConnection);

        isOpen = true;
        while ((line = readNextLine()) != null) {
            line = line.trim();

            String result = processCommandLine(line, ctx);
            // break the loop for exit
            if (CliCommand.exitCommand().getName().equals(result)) {
                printLine("Closing console.");
                break;
            } else if (result != null) {
                printLine(result);
            }
        }
    }

    /**
     */
    private String processCommandLine(String line, CmdCallContext ctx) {

        CliCommandLine cmdLine = new CliCommandLine(line);
        CliCommand<CmdCallContext> cliCmd = null;
        if (isConnected()) {
            if(cmdLine.isCommand("cls")) {
                //doing an explicite clear screen if connected
                cliCmd = commandRegistry.getCommand(cmdLine.getCommandName());
                printLine(cliCmd.execute(cmdLine.getArgs(), ctx));
                serverConnection.send("");
            } else {
                serverConnection.send(line);
            }
        } else {
            if (cmdLine.isUseable()) {
                cliCmd = commandRegistry.getCommand(cmdLine.getCommandName());
                return cliCmd.execute(cmdLine.getArgs(), ctx);
            }
        }
        return null;
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
        printLine("Connection to server terminated.");
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
    public PasswordObject readPassword(String promptText) throws IOException {
        Console console = System.console();
        if (console != null) {
            return new PasswordObject(console.readPassword("%s", promptText));
        }

        synchronized (outLock) {
            systemOut.print(promptText);
            systemOut.print(CONCEAL_ON);
            systemOut.flush();
        }
        try { // NOSONAR explicit no try with resources
            String line = systemIn.readLine();
            return new PasswordObject(line != null ? line.toCharArray() : new char[0]);
        } finally {
            synchronized (outLock) {
                systemOut.print(CONCEAL_OFF);
                systemOut.flush();
            }
        }
    }
}
