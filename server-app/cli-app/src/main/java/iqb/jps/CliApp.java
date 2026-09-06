/* Authored by iqbserve.de */
package iqb.jps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import iqb.jps.appcomp.CliConsole;
import iqb.jps.appcomp.ServerConnection;

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

    private final CliConfig config;
    private CliConsole console;

    private CliApp(CliConfig config) {
        this.config = config;
    }

    /**
     */
    public static void main(String[] args) {
        CliApp app = new CliApp(new CliConfig(args));
        app.start();
    }

    private void start() {
        if(System.console() == null) {
            LOG.warn("System.console() is NOT available. Limited functionality and security.");
        }
        LOG.info("Start JPS CLI App at: [{}] - [{}]", config.getHost(), config.getPort());

        try {
            console = new CliConsole(new ServerConnection(config));
            console.open();
        } catch (IOException e) {
            console.close();
            LOG.error("Failed to open/run CLI console", e);
        }
    }

}
