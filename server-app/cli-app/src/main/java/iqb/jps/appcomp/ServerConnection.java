/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.CliApp.CliConfig;

public class ServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConnection.class);

    private CliConfig config;
    private Socket socket = null;
    private Charset encoding = StandardCharsets.UTF_8;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    // true while disconnect() closed the socket on purpose, so the reader thread
    // doesn't report it as a lost connection
    private volatile boolean manualDisconnect = false;

    private Reader socketIn;
    private PrintWriter socketOut;

    public ServerConnection(CliConfig config) {
        this.config = config;
    }

    /**
     * @param onConnectionLost invoked (on the reader thread) if the connection drops
     *                         unexpectedly, i.e. not via a local disconnect()/exit call
     */
    public boolean connect(Consumer<String> serverOutputConsumer, Runnable onConnectionLost) {
        if (isConnected()) {
            return true;
        }
        try {
            manualDisconnect = false;
            socket = new Socket();
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), 2000);

            socketOut = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), encoding), true);
            socketIn = new InputStreamReader(socket.getInputStream(), encoding);

            Thread readerThread = new Thread(() -> readServerOutput(socketIn, serverOutputConsumer, onConnectionLost),
                    "CliApp Server connection");
            readerThread.setDaemon(true);
            readerThread.start();
            connected.set(true);

        } catch (IOException e) {
            LOG.debug("Could not connect to [{}:{}]: {}", config.getHost(), config.getPort(), e.getMessage());
            connected.set(false);
            try {
                socket.close();
            } catch (IOException ignore) {
                // already failed to connect - nothing to do
            }
        }
        return isConnected();
    }

    /**
     */
    public void send(String message) {
        if (isConnected() && message != null) {
            socketOut.println(message);
            socketOut.flush();
        }
    }

    public void disconnect() {
        if (isConnected()) {
            manualDisconnect = true;
            try {
                if (socket != null) {
                    socket.close();
                    socketOut.close();
                }
            } catch (IOException e) {
                LOG.error("Failed to close server socket", e);
            } finally {
                connected.set(false);
            }
        }
    }

    /**
     * Reads the server output in a separate thread.
     */
    private void readServerOutput(Reader socketIn, Consumer<String> serverOutputConsumer, Runnable onConnectionLost) {
        try {
            char[] buffer = new char[1024];
            int read;
            String line = "";
            while ((read = socketIn.read(buffer)) != -1) {
                line = new String(buffer, 0, read);
                serverOutputConsumer.accept(line);
            }
        } catch (IOException e) {
            // socket was closed - nothing to do
        } finally {
            connected.set(false);
            if (!manualDisconnect && onConnectionLost != null) {
                onConnectionLost.run();
            }
        }
    }

    /**
     */
    public boolean isConnected() {
        return connected.get();
    }
}
