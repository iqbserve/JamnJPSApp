/* Authored by iqbserve.de */
package iqb.jps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import iqb.jps.appcomp.CliConsole;
import iqb.jps.appcomp.CmdCallContext;
import iqb.jps.appcomp.ServerConnection;
import iqb.jps.cli.CliCommandRegistry;
import iqb.jps.cli.CliCommand;

/**
 * <pre>
 * Standalone command line interface app 
 * that is capable to connect to the JamnJPSApp via a local socket connection.
 * 
 * The app serves as a CLI client to the JPSApp command interface.
 * 
 * Supported arguments: cli.port=<port>
 * </pre>
 */
public class CliApp {

    private static final Logger LOG = LoggerFactory.getLogger(CliApp.class);

    private final CliCommandRegistry<CmdCallContext> commandRegistry;
    private final CliConfig config;

    private CliApp(CliConfig config) {
        this.config = config;
        this.commandRegistry = new CliCommandRegistry<>();
        createCommands();
    }

    /**
     */
    public static void main(String[] args) {
        CliApp app = new CliApp(new CliConfig(args));
        app.start();
    }

    /**
     */
    private void start() {
        if (System.console() == null) {
            LOG.warn("System.console() is NOT available. Limited functionality and security.");
        }
        LOG.info("Start JPS CLI App at: [{}] - [{}]", config.getHost(), config.getPort());

        CliConsole console = new CliConsole(new ServerConnection(config), commandRegistry);
        try {
            console.open();
        } catch (IOException e) {
            console.close();
            LOG.error("Failed to open/run CLI console", e);
        }
    }

    /**
     */
    public static class CliConfig {
        private String host = "localhost";
        private int port = 9091;

        public CliConfig(String[] args) {
            for (String arg : args) {
                if (arg.startsWith("cli.port=")) {
                    port = Integer.parseInt(arg.substring("cli.port=".length()));
                }
            }
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    /**
     * The provided commands of the app.
     */
    private void createCommands() {

        commandRegistry
                .addCommand(new CliCommand<CmdCallContext>("cls", (cmdArgs, ctx) -> CliCommand.ANSI_CODE_CLEAR_SCREEN));

        commandRegistry.addCommand(new CliCommand<CmdCallContext>("exit", (cmdArgs, ctx) -> {
            if (ctx.getServerConnection().isConnected()) {
                ctx.getServerConnection().disconnect();
                return "Disconnected from the server.";
            }
            return "exit";
        }));

        commandRegistry.addCommand(new CliCommand<CmdCallContext>("connect", (cmdArgs, ctx) -> {
            if (!ctx.getServerConnection().isConnected() && !ctx.getServerConnection().connect()) {
                return "Failed to connect. Probably the server is not started.";
            }
            return null;
        }));
    }
}
