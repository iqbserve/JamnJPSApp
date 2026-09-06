/* Authored by iqbserve.de */
package iqb.jps.cliapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.core.HelperTool;

/**
 * <pre>
 * CLIInterface provides a simple "command-line" interface over a local socket.
 * It allows clients to connect, send commands, and receive responses.
 * </pre>
 */
public class CliInterface {

    private static Logger LOG = LoggerFactory.getLogger(CliInterface.class);
    private static final HelperTool Tool = HelperTool.getInstance();
    private static final CliCommandRegistry CmdRegistry = CliCommandRegistry.getInstance();

    private int port = 9091;
    private ServerSocket serverSocket = null;
    private AtomicBoolean working = new AtomicBoolean(true);
    private Charset encoding = StandardCharsets.UTF_8;
    private String prompt = "jps> ";

    private BiFunction<String, CliCmdCallContext, String> commandProcessor = (line, ctx) -> line;
    /**
     * The default command processor for the CLI interface.
     */
    public static final BiFunction<String, CliCmdCallContext, String> DefaultCommandProcessor = (cmdLine, ctx) -> {
        String name = "";
        String[] args = {};
        String[] token = null;
        if (cmdLine != null && !cmdLine.isBlank()) {
            token = Tool.parseCommandLine(cmdLine);
            if (token.length >= 1) {
                name = token[0];
            } else {
                return "";
            }
            if (token.length >= 2) {
                args = new String[token.length - 1];
                System.arraycopy(token, 1, args, 0, args.length);
            }
            CliCommand cmd = CmdRegistry.getCommand(name);
            return cmd.execute(args, ctx);
        }
        return "";
    };

    /**
     */
    public CliInterface(int port) {
        this.port = port;
    }

    /**
     * <pre>
     * The command processor function is the concrete implementation of the command line string processing logic.
     * something like:
     * cliInterface.setCommandProcessor((line, ctx) -> {
     *     // parse the command line input
     *     String[] token = line.split(" ");
     *     ...
     *     return result;
     * });
     * </pre>
     */
    public CliInterface setCommandProcessor(BiFunction<String, CliCmdCallContext, String> commandProcessor) {
        this.commandProcessor = commandProcessor;
        return this;
    }

    /**
     */
    public CliInterface setEncoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     */
    public CliInterface setPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     */
    public synchronized void start() throws IOException {
        if (serverSocket == null) {
            serverSocket = new ServerSocket(port);

            CliThread cliThread = new CliThread();
            cliThread.setName(getClass().getSimpleName() + " - on Port [" + port + "]");

            cliThread.start();
        }
    }

    /**
     */
    public synchronized void close() {
        working.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.error("Unexpected error closing CLI Interface", e);
            } finally {
                serverSocket = null;
            }
        }
    }

    /**
     */
    private class CliThread extends Thread {
        @Override
        public void run() {
            LOG.info("CLI Interface started on port [{}]", port);

            try {
                ServerSocket activeSocket = serverSocket;
                while (working.get() && activeSocket != null && !activeSocket.isClosed()) {
                    handleConnection(activeSocket);
                }
            } catch (Exception e) {
                if (working.get()) {
                    LOG.error("CLI Interface encountered an error", e);
                }
            }
        }

        /**
         * Handles an incoming connection on the given server socket.
         */
        private void handleConnection(ServerSocket activeSocket) {
            try (Socket socket = activeSocket.accept();
                    PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), encoding), true);

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), encoding))) {
                LOG.info("CLI Interface connected");

                CliCmdCallContext ctx = new DefaultCallContext(in, out);
                out.println("Connected to Jamn JPSApp CLI!");
                out.print(prompt);
                out.flush();

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (!line.isBlank()) {
                        if ("exit".equals(line)) {
                            break;
                        }
                        // call the provided command processor
                        out.println(commandProcessor.apply(line, ctx));
                    }
                    out.print(prompt);
                    out.flush();
                }
            } catch (IOException _) {
                // nothing to do - we got disconnected
            } finally {
                LOG.info("CLI Interface disconnected");
            }
        }
    }

    /**
     * An argument object passed to the command execution function.
     */
    public static interface CliCmdArgs {

        public boolean hasArg(String name);

        public String getArgValue(String name);
    }

    /**
     * A context object passed to the command execution function, providing handling
     * functions.
     */
    public static interface CliCmdCallContext {

        public String queryInput(String prompt);
    }

    /**
     * A command object implementation providing a unique name and execution
     * function.
     */
    public static class CliCommand {

        public static final CliCommand UnknownCommand = new CliCommand("Unknown", (args, ctx) -> "Unknown command");

        private String name;
        private BiFunction<CliCmdArgs, CliCmdCallContext, String> commandFunction;

        public CliCommand(String name, BiFunction<CliCmdArgs, CliCmdCallContext, String> commandFunction) {
            this.name = name;
            this.commandFunction = commandFunction;
        }

        /**
         */
        public String execute(String[] args, CliCmdCallContext context) {
            return commandFunction.apply(new DefaultCliArgs(args), context);
        }

        /**
         */
        public String getName() {
            return name;
        }
    }

    /**
     * A default implementation of the command call context
     */
    private static class DefaultCallContext implements CliCmdCallContext {
        private BufferedReader in;
        private PrintWriter out;

        DefaultCallContext(BufferedReader in, PrintWriter out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public String queryInput(String prompt) {
            out.print(prompt + ": ");
            out.flush();
            try {
                return in.readLine().trim();
            } catch (Exception _) {
                return "";
            }
        }
    }

    /**
     * A default implementation of the command arguments object.
     */
    private static class DefaultCliArgs implements CliCmdArgs {

        private String[] args;

        public DefaultCliArgs(String[] args) {
            this.args = args;
        }

        @Override
        public boolean hasArg(String name) {
            String propName = name + "=";
            for (String arg : args) {
                if (arg.equals(name) || arg.startsWith(propName)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getArgValue(String name) {
            String propName = name + "=";
            for (String arg : args) {
                if (arg.startsWith(propName)) {
                    return arg.substring(propName.length());
                }
            }
            return null;
        }
    }
}
