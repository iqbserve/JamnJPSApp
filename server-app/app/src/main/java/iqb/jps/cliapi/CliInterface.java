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

import iqb.jps.cli.CliCmdCallContext;
import iqb.jps.cli.CliCommand;
import iqb.jps.cli.CliCommandLine;
import iqb.jps.cli.CliCommandRegistry;

/**
 * <pre>
 * CLIInterface provides a simple "command-line" interface over a local socket.
 * It allows clients to connect, send commands, and receive responses.
 * </pre>
 */
public class CliInterface {

    private static Logger LOG = LoggerFactory.getLogger(CliInterface.class);
    private CliCommandRegistry<CliCmdCallContext> registry = new CliCommandRegistry<CliCmdCallContext>();

    private int port = 9091;
    private ServerSocket serverSocket = null;
    private AtomicBoolean working = new AtomicBoolean(true);
    private Charset encoding = StandardCharsets.UTF_8;
    private String prompt = "jps> ";

    // provide a default implementation
    private BiFunction<String, CliCmdCallContext, String> commandProcessor = (cmdLineSource, ctx) -> {
        CliCommandLine cmdLine = new CliCommandLine(cmdLineSource);
        if (cmdLine.isUseable()) {
            CliCommand<CliCmdCallContext> cmd = registry.getCommand(cmdLine.getCommandName());
            return cmd.execute(cmdLine.getArgs(), ctx);
        }
        return "";
    };

    /**
     */
    public CliInterface(int port) {
        this.port = port;
    }

    public CliInterface setCommandRegistry(CliCommandRegistry<CliCmdCallContext> registry) {
        this.registry = registry;
        return this;
    }

    /**
     * <pre>
     * The command processor function is the concrete implementation of the command line string processing logic.
     * See this default implementation for an example.
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

                CliCmdCallContext ctx = new CliCmdCallContext(in, out);
                out.println("Connected to Jamn JPSApp CLI!");
                out.print(prompt);
                out.flush();

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (!line.isBlank()) {
                        // call the provided command processor
                        String result = commandProcessor.apply(line, ctx);
                        if (CliCommand.exitCommand().getName().equals(result)) {
                            break;
                        } else {
                            out.println(result);
                        }
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
}
