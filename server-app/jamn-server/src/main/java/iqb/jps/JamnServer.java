/* Authored by iqbserve.de */

package iqb.jps;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.JamnServer.HttpHeader.Field;
import iqb.jps.JamnServer.HttpHeader.FieldValue;
import iqb.jps.JamnServer.HttpHeader.Status;
import iqb.jps.core.AppConfig;

/**
 * <pre>
 * Just another micro node Server
 *
 * Jamn is an experimental, lightweight socket-based text data server
 * designed for smallness, independence and easy customization.
 *
 * The purpose is text data based interprocess communication.
 *
 * The structure consists of plugable components:
 * - server kernel with socket and multi-threaded connection setup
 * - request processor
 * - content provider
 *
 * All components can be easily changed or replaced.
 *
 * The default request processor relies on a basic HTTP subset.
 * It can read incoming HTTP Header/Body messages
 * and responds with an so fare appropriate HTTP message.
 * Plugable Content-Provider expand this capabilities.
 *
 * IMPORTANT:
 * How ever - Jamn IS NOT a HTTP/Web Server Implementation - this is NOT intended.
 * 
 * Jamn does NOT offer complete support for the HTTP protocol.
 * It just supports a subset - that is required and suitable for the cases
 *  - text data based network/interprocess communication with
 *    - REST like webservices
 *    - and WebSocket
 *  - and the ability to serve Browser based Applications (HTML/RIA)
 *
 * </pre>
 */
public class JamnServer {

    private static final Logger LOG = LoggerFactory.getLogger(JamnServer.class);

    // JamnServer web id - just used for http header info
    public static final String JamnServerWebID = "JamnServer/0.0.1";

    // note page - to show that NO content provider is installed by default
    private static String BlankServerPage;

    static {
        try {
            InputStream in = JamnServer.class.getResourceAsStream("/blank-server.html");
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                BlankServerPage = reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (RuntimeException e) {
            throw new UncheckedJamnServerException("ERROR - Jamn Server static initialization failed", e);
        }
    }

    public static final String LF = "\n";
    public static final String CRLF = "\r\n";
    public static final String SOCKET_IDTEXT = "socket.idtext";
    public static final String SOCKET_USAGE = "socket.usage";
    public static final String SOCKET_EXCEPTION = "socket.exception";
    public static final String REQUEST_HEADER_TEXT = "request.header.text";

    protected AppConfig config = null;

    protected ServerThread serverThread = null;
    protected ServerSocket serverSocket = null;
    protected URI serverURI = null;
    protected ExecutorService requestExecutor = null;
    protected RequestProcessor requestProcessor = null;

    public JamnServer() {
        // default port in config is: 8099
        initialize();
    }

    public JamnServer(AppConfig config) {
        // default port in config is: 8099
        this.config = config;
        initialize();
    }

    /**
     * Just for running in development
     */
    public static void main(String[] args) { //NOSONAR
        JamnServer server = new JamnServer();
        // enable all CORS - simplification for using Testfiles in a browser
        server.getConfig().setAllowAllCORSEnabled(true);
        server.start();
    }

    /**
     */
    protected void initialize() {
        requestProcessor = new HttpDefaultRequestProcessor(config);
    }

    /**
     */
    protected ServerSocket createServerSocket() throws IOException {
        int port = config.getHttpServerPort();
        ServerSocket socket = null;

        try {
            if (!System.getProperty("javax.net.ssl.keyStore", "").isEmpty()
                    && !System.getProperty("javax.net.ssl.keyStorePassword", "").isEmpty()) {
                socket = SSLServerSocketFactory.getDefault().createServerSocket(port);
            } else {
                socket = ServerSocketFactory.getDefault().createServerSocket(port);
            }

            socket.setReuseAddress(true);
            if (port == 0) {
                config.setActualHttpServerPort(socket.getLocalPort());
            }
        } catch (Exception e) {
            if (socket != null) {
                socket.close();
            }
            throw e;
        }
        return socket;
    }

    /**
     */
    protected void determineServerURI(ServerSocket socket) throws IOException {
        String scheme = "http";
        if (socket instanceof SSLServerSocket) {
            scheme = "https";
        }
        try {
            this.serverURI = new URI(scheme + "://localhost:" + config.getHttpServerPort());
        } catch (URISyntaxException e) {
            throw new IOException("Error creating server URI", e);
        }
    }

    /**
     * Internal - create/setup and start kernel server thread and socket.
     */
    protected synchronized void startListening() throws IOException {
        if (isRunning()) {
            return;
        }

        if (requestExecutor == null || requestExecutor.isShutdown()) {
            requestExecutor = Executors.newFixedThreadPool(config.getHttpWorkerNumber());
        }
        serverSocket = createServerSocket();
        determineServerURI(serverSocket);

        serverThread = new ServerThread(config.getHttpClientSocketTimeout());
        serverThread.setName(getClass().getSimpleName() + " - on Port [" + config.getHttpServerPort() + "]");
        serverThread.start();
    }

    /**
     * Internal - stop/close kernel server thread and socket.
     */
    protected synchronized void stopListening() {
        if (isRunning()) {
            try {
                serverSocket.close();
            } catch (IOException _) {
                // OK this is specified
            }
        }
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.shutdown();
        }
        if (requestExecutor != null) {
            requestExecutor.shutdownNow();
        }
    }

    /**
     */
    public synchronized void start() {
        try {
            startListening();
            String crlf = "\n # ";
            LOG.atInfo().log(new StringBuilder("#")
                    .append(crlf)
                    .append("JamnServer STARTED:").append(crlf)
                    .append(" ").append(serverURI.toString()).append(" - ").append(serverSocket.toString())
                    .append(crlf)
                    .append(config.isHttpAllowAllCORSEnabled()
                            ? crlf + "IMPORTANT - Global ALLOW ALL CORS is enabled!" + crlf
                            : "")
                    .toString());
        } catch (Exception e) {
            if (e instanceof BindException) {
                LOG.error("Probably ALREADY RUNNING SERVER on port [{}]", getConfig().getHttpServerPort(), e);
            }
            stop();
            throw new UncheckedJamnServerException("JamnServer start failed", e);
        }
    }

    /**
     */
    public synchronized void stop() {
        boolean wasRunning = isRunning();
        stopListening();
        if (wasRunning) {
            LOG.info("JamnServer STOPPED");
        }
    }

    /**
     */
    public URI getURI() {
        return serverURI;
    }

    /**
     */
    public boolean isRunning() {
        return (serverSocket != null && !serverSocket.isClosed());
    }

    /**
     */
    public AppConfig getConfig() {
        return config;
    }

    /*********************************************************
     * <pre>
     * The server plugin interfaces for customer extensions and provider.
     * </pre>
     *********************************************************/

    /**
     */
    public JamnServer addContentProvider(String id, ContentProvider provider) {
        requestProcessor.addContentProvider(id, provider);
        return this;
    }

    /**
     */
    public JamnServer setRequestProcessor(RequestProcessor processor) {
        requestProcessor = processor;
        return this;
    }

    /**
     */
    public RequestProcessor getRequestProcessor() {
        return requestProcessor;
    }

    /*********************************************************
     * <pre>
     * The Server socket listener Thread.
     * It starts via requestExecutor a worker thread for every incoming connection
     * and delegates the client socket to a central requestProcessor.
     * </pre>
     *********************************************************/
    /**
     */
    protected class ServerThread extends Thread {
        private volatile boolean work = true;

        private int clientSocketTimeout = 10000;

        public ServerThread(int clientSocketTimeout) {
            this.clientSocketTimeout = clientSocketTimeout;
        }

        public synchronized void shutdown() {
            work = false;
        }

        @Override
        public void run() {

            try {
                ServerSocket activeSocket = JamnServer.this.serverSocket; // keep local

                while (work && activeSocket != null && !activeSocket.isClosed()) {

                    final Socket clientSocket = activeSocket.accept();

                    // start request execution in its own thread
                    requestExecutor.execute(() -> {
                        Map<String, String> comData = HashMap.newHashMap(5);
                        long start = System.currentTimeMillis();
                        try {
                            try {
                                clientSocket.setSoTimeout(clientSocketTimeout);
                                clientSocket.setTcpNoDelay(true);
                                // delegate the concrete request handling
                                requestProcessor.handleRequest(clientSocket, comData);

                            } finally {
                                try {
                                    if (!(clientSocket instanceof SSLSocket)) {
                                        clientSocket.shutdownOutput(); // first step only output
                                    }
                                } finally {
                                    clientSocket.close();
                                    LOG.debug(String.format("%s %s %s %s %s",
                                            comData.getOrDefault(SOCKET_IDTEXT, "unknown"),
                                            "closed [" + (System.currentTimeMillis() - start) + "]",
                                            "usage [" + comData.getOrDefault(SOCKET_USAGE, "") + "]",
                                            "exp [" + comData.getOrDefault(SOCKET_EXCEPTION, "") + "]",
                                            Thread.currentThread().getName()));
                                }
                            }
                        } catch (IOException _) {
                            // nothing to do
                        }
                    });
                }
            } catch (IOException _) {
                // nothing to do
            } finally {
                LOG.debug("ServerThread finished: [{}]", Thread.currentThread().getName());
            }
        }
    }

    /*********************************************************
     * <pre>
     * The central processing interfaces and default implementations.
     * Chain: ServerThread -> RequestProcessor -> ContentProvider
     * </pre>
     *********************************************************/
    /**
     * <pre>
     * The Interface is used by the RequestProcessor to create the data content for a request.
     * Or to delegate a protocol upgrade to a specific handler e.g. WebSocket.
     * </pre>
     */
    public static interface ContentProvider {

        public static final String WEB_CONTENT = "ContentProvider";
        public static final String WEB_SERVICE = "ServiceProvider";
        public static final String WEB_SOCKET = "WebSocketProvider";

        /**
         * Standard Interface for Content Provider
         */
        void handleContentProcessing(RequestMessage request, ResponseMessage response);

        /**
         * Extended Interface for Provider like WebSocket
         */
        default void handleContentProcessing(RequestMessage request, Socket socket, Map<String, String> comData)
                throws IOException {
            throw new UnsupportedOperationException("Call to ContentProvider unimplemented default interface method");
        }

        default void setPathMapper(BiFunction<String, RequestMessage, String> mapper) {
        }
    }

    /**
     * <pre>
     * The Interface used by the server socket thread to delegate 
     * the protocol specific processing of incoming client connection/requests.
     * </pre>
     */
    public static interface RequestProcessor {

        /**
         * The central interface method called from the server thread.
         *
         * @param socket
         * @param comData - internal communication data
         * @throws IOException
         */
        void handleRequest(Socket socket, Map<String, String> comData) throws IOException;

        /**
         * The interface to set the content provider that creates the use case specific
         * response content.
         */
        void addContentProvider(String id, ContentProvider provider);

        /**
         * The interface to set the content provider dispatcher that decides which
         * content provider is used for a request.
         */
        void setProviderDispatcher(Function<RequestMessage, String> request);
    }

    /**
     * <pre>
     * The RequestProcessor is the interface called by the socket layer 
     * and thus the entry point for processing.
     *  - handleRequest(...)
     * 
     * The Default-RequestProcessor implements the basic JamnServer http layer
     * but it uses EMPTY provider and pre processing.
     * The processor just reads data from the underlying socket
     * and tries to interpret/transform it into a http message consisting of a http-header and body.
     * 
     * ALL further processing functionality and logic has to be implemented by providers etc.
     * 
     * The processor also branches to an upgrade handler in case of a Web-Socket connection request.
     * If a Web-Socket connection can then be established
     * the further client/server communication is completely split off.
     * 
     * </pre>
     */
    public static class HttpDefaultRequestProcessor implements RequestProcessor {
        protected AppConfig config;
        protected String encoding = StandardCharsets.UTF_8.name();
        protected boolean keepAliveEnabled = false;
        protected boolean isHttpAllowAllCORSEnabled = false;

        /**
         */
        public HttpDefaultRequestProcessor(AppConfig config) {
            this.config = config;
            this.encoding = config.getHttpEncoding();
            this.keepAliveEnabled = config.isHttpConnectionKeepAlive();
            this.isHttpAllowAllCORSEnabled = config.isHttpAllowAllCORSEnabled();
        }

        // the available ContentProvider
        protected Map<String, ContentProvider> contentProviderMap = new HashMap<>();
        protected Function<RequestMessage, String> contentProviderDispatcher = (RequestMessage request) -> "unknown";

        // empty default content provider
        // just returning the blank server page at root
        // or status SC_204_NO_CONTENT
        protected ContentProvider defaultContentProvider = (RequestMessage request, ResponseMessage response) -> {
            LOG.warn("Request to EMPTY Default Content Provider");

            response.setStatus(Status.SC_204_NO_CONTENT);
            try {
                if ("/".equals(request.getPath())) {
                    response.setContentType(FieldValue.TEXT_HTML);
                    response.writeToContent(BlankServerPage.getBytes());
                    response.setStatus(Status.SC_200_OK);
                }
            } catch (IOException _) {
                // ignore in empty default implementation
            }
        };

        /**
         */
        public void setProviderDispatcher(Function<RequestMessage, String> dispatcher) {
            this.contentProviderDispatcher = dispatcher;
        }

        /**
         * <pre>
         * The actual top level request handling implementation called from the server thread.
         * </pre>
         */
        @Override
        public void handleRequest(Socket socket, Map<String, String> comData) throws IOException {

            String socketIDText = String.format("ClientSocket [%s]", socket.hashCode());
            comData.put(SOCKET_IDTEXT, socketIDText);

            InputStream inStream = new BufferedInputStream(socket.getInputStream(), getInitialBufferSizeFor("in"));
            OutputStream outStream = new BufferedOutputStream(socket.getOutputStream(),
                    getInitialBufferSizeFor("out"));

            RequestMessage request = null;
            ResponseMessage response = null;
            ContentProvider contentProvider = null;

            boolean keepAlive = false;
            // a usage counter for debugging purpose
            int usage = 0;
            try {
                LOG.atDebug().log(() -> String.format("%s %s %s %s", socketIDText, "opened", socket.toString(),
                        Thread.currentThread().getName()));

                do {
                    keepAlive = false;
                    response = new ResponseMessage(outStream, new HttpHeader()
                            .setContentType(FieldValue.TEXT_PLAIN)
                            .setContentLength("0")).addContextData(socketIDText);

                    String headerText = readHeader(inStream);
                    response.contextData.add(headerText);

                    request = new RequestMessage(newHeader(headerText));
                    request.setBody(readBody(inStream, request.getContentLength(), request.getEncoding()));
                    decodeRequestPath(request);

                    // interface to call any protocol or app specific processing
                    // before content providing
                    // this may trigger an immediate response
                    doRequestPreProcessing(request, response);

                    if (response.isNotProcessed()) {
                        // route request to the required content provider
                        // check for WebSocket upgrade request
                        if (request.header().isWebSocket()) {
                            // explicit switch to WebSocket processing
                            comData.put(JamnServer.REQUEST_HEADER_TEXT, headerText);
                            contentProvider = getContentProviderFor(request);
                            contentProvider.handleContentProcessing(request, socket, comData);
                        } else {
                            keepAlive = checkForKeepAliveConnection(request, response);

                            // create and send the response content
                            contentProvider = getContentProviderFor(request);
                            contentProvider.handleContentProcessing(request, response);
                            if (response.isNotProcessed()) {
                                response.send();
                            }
                            usage++;
                        }
                    }
                    // if keep-alive loop until socket timeout
                } while (keepAlive && keepAliveEnabled);
            } catch (InterruptedIOException e) {
                comData.put(SOCKET_EXCEPTION, e.getMessage());
                interruptCleanUp(socketIDText, inStream, outStream);
            } catch (SecurityException _) {
                // send 403 for any security exception
                response.sendStatus(Status.SC_403_FORBIDDEN);
            } catch (Exception e) {
                LOG.error(String.format("Request handling internal ERROR: %s", socketIDText), e);
                // send 500 for any other exception
                response.sendStatus(Status.SC_500_INTERNAL_ERROR);
            } finally {
                response.close();
                comData.put(SOCKET_USAGE, String.valueOf(usage));
            }
        }

        /**
         */
        @Override
        public void addContentProvider(String id, ContentProvider provider) {
            contentProviderMap.put(id, provider);
        }

        /**
         * Dispatches a request to the appropriate content provider.
         * With the exception of WebSocket requests, which are handled directly.
         */
        protected ContentProvider getContentProviderFor(RequestMessage request) {

            // this is not configurable or dispatchable
            if (request.header().isWebSocket()) {
                if (contentProviderMap.containsKey(ContentProvider.WEB_SOCKET)) {
                    return contentProviderMap.get(ContentProvider.WEB_SOCKET);
                } else {
                    throw new UncheckedJamnServerException(
                            "No ContentProvider registered for WebSocket request [" + request.getDecodedPath() + "]");
                }
            }

            // be gentle
            if (contentProviderMap.isEmpty()) {
                return defaultContentProvider;
            }

            String providerId = contentProviderDispatcher.apply(request);
            if (contentProviderMap.containsKey(providerId)) {
                return contentProviderMap.get(providerId);
            }

            throw new UncheckedJamnServerException(
                    String.format("No ContentProvider [%s] found for request [%s]", providerId,
                            request.getDecodedPath()));
        }

        /**
         * to be overwritten
         * 
         * @param request may be used in overriding classes
         * @throws IOException may be used in overriding classes
         */
        protected void doRequestPreProcessing(RequestMessage request, ResponseMessage response)
                throws IOException, SecurityException {
            if (this.isHttpAllowAllCORSEnabled) {
                response.header().set(HttpHeader.Field.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                response.header().set(HttpHeader.Field.ACCESS_CONTROL_ALLOW_METHODS, "*");
                response.header().set(HttpHeader.Field.ACCESS_CONTROL_ALLOW_HEADERS, "*");
            }
        }

        /**
         * the decoded path is the resource path without parameter
         */
        protected String decodeRequestPath(RequestMessage request) {
            String path = request.getPath();

            if (path.contains("?")) {
                String[] parts = path.split("\\?");
                request.setDecodedPath(parts[0]);

                if (parts.length > 1) {
                    Map<String, String> parameter = new HashMap<>();
                    String[] params = parts[1].split("\\&");
                    for (String param : params) {
                        parts = param.split("=");
                        parameter.put(parts[0], parts[1]);
                    }
                    request.setParameter(parameter);
                }
            } else {
                request.setDecodedPath(path);
            }
            return request.getDecodedPath();
        }

        /**
         */
        protected String readHeader(InputStream inStream) throws IOException {
            byte[] bytes;
            int curByte = 0;
            int headerEndFlag = 0;

            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            do {
                if ((curByte = inStream.read()) == -1) {
                    break; // end of stream, because inStream.available() may ? not
                           // be reliable enough
                }

                if (curByte == 13) { // CR
                    byteBuffer.write(curByte);
                    if ((curByte = inStream.read()) == 10) { // LF
                        byteBuffer.write(curByte);
                        headerEndFlag++;
                    } else if (curByte != -1) {
                        byteBuffer.write(curByte);
                    }
                } else {
                    byteBuffer.write(curByte);
                    if (headerEndFlag == 1) {
                        headerEndFlag--;
                    }
                }
            } while (headerEndFlag < 2 && inStream.available() > 0);

            bytes = byteBuffer.toByteArray();
            return new String(bytes, this.encoding).trim();
        }

        /**
         */
        protected HttpHeader newHeader(String headerText) {
            return new HttpHeader(Collections.unmodifiableMap(parseHttpHeader(headerText)));
        }

        /**
         * <pre>
         * Tries to blocking read the request body from the socket InputStream.
         * </pre>
         */
        protected String readBody(InputStream inStream, int contentLength, String encoding) throws IOException {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int byteVal = 0;
            int actual = 0;
            int available = 0;

            if (contentLength > 0) {
                while ((available = inStream.available()) > 0 || actual < contentLength) {
                    actual++;
                    byteVal = inStream.read();
                    if (byteVal == -1) {
                        break;
                    }
                    byteBuffer.write(byteVal);
                }
                if (actual != contentLength || available > 0) {
                    String msg = String.format("Http body read: actual [%s] header [%s] available [%s]", actual,
                            contentLength,
                            available);
                    LOG.warn(msg);
                }
            }
            return new String(byteBuffer.toByteArray(), encoding);
        }

        /**
         * Parse HTTP header lines to key/value pairs. This method includes the http
         * status line as "self defined attributes" (path, method, version etc.) in the
         * map.
         */
        protected Map<String, String> parseHttpHeader(String header) {
            Map<String, String> fields = new LinkedHashMap<>();
            String[] lines = header.split("\\n");
            StringBuilder val;

            for (int i = 0; i < lines.length; i++) {
                String[] parts = null;
                String line = lines[i];

                if (i == 0) {
                    fields.putAll(parseHttpHeaderStatusLine(line));
                } else if (line.contains(":")) {
                    parts = line.split(":");
                    if (parts.length == 2) {
                        fields.put(parts[0].trim(), parts[1].trim());
                    } else if (parts.length > 2) {
                        val = new StringBuilder(parts[1].trim());
                        for (int k = 1; k + 1 < parts.length; k++) {
                            val.append(":").append(parts[k + 1].trim());
                        }
                        fields.put(parts[0].trim(), val.toString());
                    }
                }
            }
            return fields;
        }

        /**
         * Parse header first line = status line.
         */
        protected Map<String, String> parseHttpHeaderStatusLine(String statusLine) {
            Map<String, String> fields = new LinkedHashMap<>();

            // parse status line to "self defined attributes"
            String[] parts = statusLine.split(" ");
            String[] subParts = null;
            if (parts.length > 0) {
                for (int i = 0; i < parts.length; i++) {
                    if (i == 0) {
                        // always bring method names to Upper Case
                        fields.put(Field.HTTP_METHOD, parts[i].trim().toUpperCase());
                    } else if (parts[i].trim().toUpperCase().contains(Field.HTTP_VERSION_MARK)) {
                        subParts = parts[i].trim().split("/");
                        if (subParts.length == 2) {
                            fields.put(Field.HTTP_VERSION, subParts[1].trim());
                        }
                    } else if (parts[i].trim().contains("/")) {
                        fields.put(Field.HTTP_PATH, parts[i].trim());
                    }
                }
            }
            return fields;
        }

        /**
         */
        protected int getInitialBufferSizeFor(String type) {
            return "in".equalsIgnoreCase(type) ? 4 * 1024 : 8 * 1024;
        }

        /**
         */
        protected boolean checkForKeepAliveConnection(RequestMessage request, ResponseMessage response) {

            if (request.header().hasConnectionKeepAlive() && keepAliveEnabled) {
                response.header().setConnectionKeepAlive();
                return true;
            } else {
                response.header().setConnectionClose();
            }
            return false;
        }

        /**
        */
        protected void interruptCleanUp(String idText, InputStream in, OutputStream out) {
            try {
                in.close();
            } catch (Exception e) {
                LOG.warn(String.format("%s ERROR closing input after timeout [%s]", idText, e.toString()));
            }
            try {
                out.close();
            } catch (Exception e) {
                LOG.warn(String.format("%s ERROR closing output after timeout [%s]", idText, e.toString()));
            }
        }
    }

    /**
     * <pre>
     * The class encapsulates HTTP header information.
     * In particular, it provides a selection of constants for status codes and header fields.
     * In addition, it serves as a wrapper around a map with key/value pairs for header fields.
     * </pre>
     */
    public static class HttpHeader {

        /**
         */
        public static String getHttpStatusStringFor(Object nr) {
            String nrStr = String.valueOf(nr).trim();
            if (Status.TEXT.containsKey(nrStr)) {
                StringBuilder status = new StringBuilder(nrStr);
                status.append(" ").append(Status.TEXT.get(nrStr));
                return status.toString();
            }
            return nrStr;
        }

        public static boolean isLocalhost(String host) {
            String hostLower = host.trim().toLowerCase();
            return (hostLower.startsWith("localhost") || hostLower.startsWith("127.0.0.1"));
        }

        protected String encoding = StandardCharsets.UTF_8.name();

        protected String[] statusline = new String[] { Field.HTTP_1_0, "" };

        protected Map<String, String> fieldMap = new LinkedHashMap<>();
        protected List<String> setCookies = null;

        public HttpHeader() {
            set(Field.SERVER, JamnServerWebID);
        }

        /**
         */
        public HttpHeader(Map<String, String> attributes) {
            fieldMap = attributes;
        }

        // the magic websocket uid to accept a connection request
        public static final String MAGIC_WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        /**
         * HTTP status codes.
         */
        public static class Status {
            protected Status() {
            }

            public static final String SC_101_SWITCH_PROTOCOLS = "101";
            public static final String SC_200_OK = "200";
            public static final String SC_204_NO_CONTENT = "204";
            public static final String SC_400_BAD_REQUEST = "400";
            public static final String SC_403_FORBIDDEN = "403";
            public static final String SC_404_NOT_FOUND = "404";
            public static final String SC_405_METHOD_NOT_ALLOWED = "405";
            public static final String SC_408_TIMEOUT = "408";
            public static final String SC_500_INTERNAL_ERROR = "500";

            public static final Map<String, String> TEXT;
            static {
                Map<String, String> map = new HashMap<>();
                map.put("101", "Switching Protocols");
                map.put("200", "OK");
                map.put("201", "Created");
                map.put("204", "No Content");
                map.put("400", "Bad Request");
                map.put("403", "Forbidden");
                map.put("404", "Not found");
                map.put("405", "Method Not Allowed");
                map.put("406", "Not Acceptable");
                map.put("408", "Request Timeout");
                map.put("411", "Length Required");
                map.put("500", "Internal Server Error");
                map.put("503", "Service Unavailable");
                TEXT = Collections.unmodifiableMap(map);
            }
        }

        /**
         * HTTP header field identifier.
         */
        public static class Field {
            protected Field() {
            }

            public static final String HTTP_1_0 = "HTTP/1.0";
            public static final String HTTP_1_1 = "HTTP/1.1";

            // statusline attributes
            public static final String HTTP_METHOD = "http-method";
            public static final String HTTP_PATH = "http-path";
            public static final String HTTP_STATUS = "http-status";
            public static final String HTTP_VERSION = "http-version";
            public static final String HTTP_VERSION_MARK = "HTTP/";

            // header field attributes
            public static final String SERVER = "Server";
            public static final String CONTENT_LENGTH = "Content-Length";
            public static final String CONTENT_TYPE = "Content-Type";
            public static final String CONNECTION = "Connection";
            public static final String HOST = "Host";
            public static final String ORIGIN = "Origin";
            public static final String UPGRADE = "Upgrade";
            public static final String SET_COOKIE = "Set-Cookie";
            public static final String COOKIE = "Cookie";
            public static final String CACHE_CONTROL = "Cache-Control";

            public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
            public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

            public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
            public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
            public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

            public static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
            public static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
            public static final String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
            public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
            public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
            public static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";
            public static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

            public static final String AUTHORIZATION = "Authorization";
            public static final String BEARER = "Bearer";

        }

        /**
         * HTTP header field values.
         */
        public static class FieldValue {
            protected FieldValue() {
            }

            public static final String CLOSE = "close";
            public static final String KEEP_ALIVE = "keep-alive";
            public static final String UPGRADE = "Upgrade";
            public static final String KEEP_ALIVE_UPGRADE = "keep-alive, Upgrade";
            public static final String WEBSOCKET = "websocket";
            public static final String TEXT = "text/";
            public static final String TEXT_PLAIN = "text/plain";
            public static final String TEXT_XML = "text/xml";
            public static final String TEXT_HTML = "text/html";
            public static final String TEXT_CSS = "text/css";
            public static final String TEXT_JS = "text/javascript";
            public static final String APPLICATION_JSON = "application/json";
            public static final String IMAGE = "image/";
            public static final String IMAGE_PNG = "image/png";
            public static final String IMAGE_X_ICON = "image/x-icon";
            public static final String IMAGE_SVG_XML = "image/svg+xml";

            public static final String NO_CACHE = "no-cache";
            public static final String NO_STORE = "no-store";
            public static final String MUST_REVALIDATE = "must-revalidate";
        }

        /**
         */
        protected static boolean equalsOrContains(String attributeVal, String val) {
            return (attributeVal.equalsIgnoreCase(val) || attributeVal.toLowerCase().contains(val.toLowerCase()));
        }

        /**
         */
        protected StringBuilder createHeader() {
            StringBuilder header = new StringBuilder(String.join(" ", statusline));
            for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
                header.append(CRLF).append(entry.getKey()).append(": ").append(entry.getValue());
            }

            if (setCookies != null && !setCookies.isEmpty()) {
                for (String entry : setCookies) {
                    header.append(CRLF).append(Field.SET_COOKIE).append(": ").append(entry);
                }
            }

            header.append(CRLF).append(CRLF);
            return header;
        }

        /**
         */
        @Override
        public String toString() {
            return createHeader().toString();
        }

        /**
         */
        public byte[] toMessageBytes(String encoding) throws IOException {
            return createHeader().toString().getBytes(encoding);
        }

        /**
         */
        public boolean has(String key, String val) {
            return equalsOrContains(fieldMap.getOrDefault(key, ""), val);
        }

        /**
         */
        public HttpHeader set(String key, String val) {
            fieldMap.put(key, val);
            return this;
        }

        /**
         */
        public void add(String[] header) {
            for (String entry : header) {
                String[] keyVal = entry.split(":");
                set(keyVal[0].trim(), keyVal[1].trim());
            }
        }

        /**
         */
        public String get(String key, String... defaultValue) {
            return fieldMap.getOrDefault(key, defaultValue.length > 0 ? defaultValue[0] : "");
        }

        /**
         */
        public HttpHeader setEncoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         */
        public String getEncoding() {
            return encoding;
        }

        /**
         */
        public HttpHeader setHttpVersion(String val) {
            statusline[0] = val;
            return this;
        }

        /**
         */
        public HttpHeader setHttpStatus(Object val) {
            String status = getHttpStatusStringFor(val);
            if (!status.isEmpty()) {
                statusline[1] = status;
            }
            return this;
        }

        /**
         */
        public HttpHeader applyAttributes(Map<String, String> attributes) {
            fieldMap.putAll(attributes);
            return this;
        }

        /**
         */
        public Map<String, String> getAttributes() {
            return this.fieldMap;
        }

        /**
         */
        public String getContentType() {
            return get(Field.CONTENT_TYPE);
        }

        /**
         */
        public int getContentLength() {
            return Integer.valueOf(get(Field.CONTENT_LENGTH, "0"));
        }

        /**
         */
        public String getWebSocketKey() {
            return get(Field.SEC_WEBSOCKET_KEY);
        }

        /**
         */
        public boolean hasConnectionKeepAlive() {
            return has(Field.CONNECTION, FieldValue.KEEP_ALIVE);
        }

        /**
         */
        public HttpHeader setConnectionKeepAlive() {
            return set(Field.CONNECTION, FieldValue.KEEP_ALIVE);
        }

        /**
         */
        public HttpHeader setConnectionClose() {
            return set(Field.CONNECTION, FieldValue.CLOSE);
        }

        /**
         */
        public boolean hasContentType(String val) {
            return has(Field.CONTENT_TYPE, val);
        }

        /**
         */
        public HttpHeader setContentType(String val) {
            return set(Field.CONTENT_TYPE, val);
        }

        /**
         */
        public HttpHeader setContentLength(String val) {
            return set(Field.CONTENT_LENGTH, val);
        }

        /**
         */
        public HttpHeader setContentLength(int val) {
            return setContentLength(String.valueOf(val));
        }

        /**
         */
        public HttpHeader setConnection(String val) {
            return set(Field.CONNECTION, val);
        }

        /**
         */
        public HttpHeader setUpgrade(String val) {
            return set(Field.UPGRADE, val);
        }

        /**
         */
        public HttpHeader setServer(String val) {
            return set(Field.SERVER, val);
        }

        /**
         */
        public HttpHeader addSetCookie(String val) {
            getSetCookies().add(val);
            return this;
        }

        /**
         */
        public List<String> getSetCookies() {
            if (setCookies == null) {
                setCookies = new ArrayList<>();
            }
            return setCookies;
        }

        /**
         */
        public String getCookie() {
            return fieldMap.getOrDefault(Field.COOKIE, "");
        }

        /**
         */
        public List<String> getCookieAsList() {
            return Arrays.asList(fieldMap.getOrDefault(Field.COOKIE, "").split(";"));
        }

        /**
         */
        public String getMethod() {
            return fieldMap.getOrDefault(Field.HTTP_METHOD, "");
        }

        /**
         */
        public String getPath() {
            return fieldMap.getOrDefault(Field.HTTP_PATH, "");
        }

        /**
         */
        public String getHost() {
            return fieldMap.getOrDefault(Field.HOST, "");
        }

        /**
         */
        public String getOrigin() {
            return fieldMap.getOrDefault(Field.ORIGIN, "");
        }

        /**
         */
        public String getAuthorization() {
            return fieldMap.getOrDefault(Field.AUTHORIZATION, "");
        }

        /**
         */
        public String getAuthorizationBearer() {
            String val = getAuthorization();
            if (val.startsWith(Field.BEARER)) {
                String[] token = val.split(" ");
                return token.length == 2 ? token[1].trim() : "";
            }
            return "";
        }

        /**
         */
        public boolean isWebSocket() {
            return !getWebSocketKey().isEmpty();
        }

        /**
         */
        public boolean isMethod(String type) {
            return getMethod().equalsIgnoreCase(type);
        }

    }

    /**
     * <pre>
     * </pre>
     */
    public static class RequestMessage {
        protected HttpHeader httpHeader = null;
        protected String bodyContent = "";
        protected String decodedPath = "";
        protected Map<String, String> parameter = null;

        public RequestMessage(HttpHeader header) {
            httpHeader = header;
        }

        /**
         */
        public HttpHeader header() {
            return httpHeader;
        }

        /**
         */
        public String body() {
            return bodyContent;
        }

        /**
         */
        public void setBody(String body) {
            bodyContent = body;
        }

        /**
         */
        public int getContentLength() {
            return httpHeader.getContentLength();
        }

        /**
         */
        public String getContentType() {
            return httpHeader.getContentType();
        }

        /**
         */
        public String getEncoding() {
            return httpHeader.getEncoding();
        }

        /**
         */
        public String getPath() {
            return httpHeader.getPath();
        }

        /**
         */
        public String getDecodedPath() {
            return decodedPath;
        }

        /**
         */
        public void setDecodedPath(String path) {
            decodedPath = path;
        }

        public boolean hasParameter() {
            return parameter != null;
        }

        public Map<String, String> getParameter() {
            return parameter;
        }

        public String getParameter(String key, String defaultValue) {
            if (parameter != null) {
                return parameter.getOrDefault(key, defaultValue);
            }
            return defaultValue;
        }

        public void setParameter(Map<String, String> parameter) {
            this.parameter = parameter;
        }

        /**
         */
        public String getMethod() {
            return httpHeader.getMethod();
        }

        /**
         */
        public boolean isMethod(String val) {
            return httpHeader.getMethod().equalsIgnoreCase(val);
        }

        /**
         */
        public boolean hasContentType(String type) {
            return httpHeader.hasContentType(type);
        }
    }

    /**
     * <pre>
     * </pre>
     */
    public static class ResponseMessage {
        protected List<String> contextData = new ArrayList<>();
        protected HttpHeader httpHeader = new HttpHeader();
        protected OutputStream outStream;
        protected ByteArrayOutputStream contentBuffer;
        protected String statusNr = "";
        protected boolean isProcessed = false;

        protected String encoding = StandardCharsets.UTF_8.name();

        public ResponseMessage(OutputStream outStream) {
            this.outStream = outStream;
        }

        public ResponseMessage(OutputStream outStream, HttpHeader header) {
            this.outStream = outStream;
            httpHeader = header;
        }

        protected ByteArrayOutputStream getContentBuffer() {
            if (contentBuffer == null) {
                contentBuffer = new ByteArrayOutputStream();
            }
            return contentBuffer;
        }

        public ResponseMessage addContextData(String data) {
            contextData.add(data);
            return this;
        }

        /**
         */
        public HttpHeader header() {
            return httpHeader;
        }

        /**
         */
        public ResponseMessage setStatus(String val) {
            statusNr = val;
            httpHeader.setHttpStatus(statusNr);
            return this;
        }

        /**
         */
        public String getStatus() {
            return statusNr;
        }

        /**
         */
        public void setProcessed() {
            isProcessed = true;
        }

        /**
         */
        public boolean isNotProcessed() {
            return !isProcessed;
        }

        /**
         */
        public ResponseMessage setContentType(String val) {
            httpHeader.setContentType(val);
            return this;
        }

        /**
         */
        public String getContentType() {
            return httpHeader.getContentType();
        }

        /**
         */
        public void writeToContent(byte[] content) throws IOException {
            getContentBuffer().write(content);
        }

        /**
         */
        public void send() throws IOException {
            writeOutResponse(outStream, getContentBuffer().toByteArray());
        }

        /**
         */
        public void sendStatus(String status) throws IOException {
            setStatus(status);
            writeOutResponse(outStream, null);
        }

        /**
         */
        public void close() throws IOException {
            outStream.flush();
        }

        /**
         * @throws IOException
         */
        protected void writeOutResponse(OutputStream out, byte[] body) throws IOException {
            byte[] messageBytes = createMessageBytesWithBody(body);

            LOG.atDebug().log(this::requestSummary);
            contextData.add(0, "<-- ALREADY SENT -->");
            out.write(messageBytes);
            out.flush();
        }

        /**
         */
        protected String requestSummary() {
            String crlf = "\n";
            String socketId = !contextData.isEmpty() ? contextData.remove(0) : "";
            StringBuilder text = new StringBuilder(crlf);
            text.append("<-- Request --> ").append(socketId).append(" - ").append(Thread.currentThread().getName())
                    .append(crlf)
                    .append(String.join(crlf, contextData)).append(crlf)
                    .append("<-- Response -->").append(crlf)
                    .append(httpHeader.toString().trim()).append(crlf);
            return text.toString();
        }

        /**
         * @throws IOException
         */
        protected byte[] createMessageBytesWithBody(byte[] body) throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();

            int bodyLen = (body != null) ? body.length : 0;
            if (bodyLen > 0) {
                httpHeader.setContentLength(bodyLen);
            }

            byte[] header = httpHeader.toMessageBytes(encoding);
            message.write(header);
            if (bodyLen > 0) {
                message.write(body);
            }
            return message.toByteArray();
        }
    }

    /**
     * <pre>
     * </pre>
     */
    public static class MimeType {

        private MimeType() {
        }

        public static String getFromPath(String path) {
            String type = FieldValue.TEXT_HTML;
            if (path.endsWith(".css")) {
                type = FieldValue.TEXT_CSS;
            } else if (path.endsWith(".js") || path.endsWith(".mjs")) {
                type = FieldValue.TEXT_JS;
            } else if (path.endsWith(".json")) {
                type = FieldValue.APPLICATION_JSON;
            } else if (isImage(path)) {
                if (path.endsWith("/favicon.ico")) {
                    type = FieldValue.IMAGE_X_ICON;
                } else if (path.endsWith(".svg")) {
                    type = FieldValue.IMAGE_SVG_XML;
                } else {
                    type = FieldValue.IMAGE + path.substring(path.lastIndexOf(".") + 1, path.length());
                }
            }
            return type;
        }

        /**
         */
        public static boolean isImage(String path) {
            return path.endsWith(".png") || path.endsWith(".jpg")
                    || path.endsWith(".gif")
                    || path.endsWith(".ico")
                    || path.endsWith(".svg");
        }

    }

    /*********************************************************
     * <pre>
     * Jamn Exceptions.
     * </pre>
     *********************************************************/
    /**
     */
    public static class UncheckedJamnServerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJamnServerException(String msg, Throwable cause) {
            super(msg, cause);
        }

        public UncheckedJamnServerException(String msg) {
            super(msg);
        }

    }
}