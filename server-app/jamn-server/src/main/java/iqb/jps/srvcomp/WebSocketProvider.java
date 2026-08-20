/* Authored by iqbserve.de */
package iqb.jps.srvcomp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import iqb.jps.JamnServer.HttpHeader.Field;
import iqb.jps.JamnServer.HttpHeader.FieldValue;
import iqb.jps.JamnServer.HttpHeader.Status;
import iqb.jps.JamnServer.HttpHeader;
import iqb.jps.JamnServer.RequestMessage;
import iqb.jps.JamnServer.ResponseMessage;
import iqb.jps.JamnServer;
import iqb.jps.JamnServer.ContentProvider;
/**
 *<pre>
 * A rudimentary WebSocket Provider implementation for the JamnServer.
 * 
 * The Provider is a one class wso "module" providing the basic protocol handling 
 * and an interface to plug in customer messaging logic.
 *  
 * The WebSocketHandler class implements the main WebSocket logic and behavior.
 * in particular - the WebSocket "message format magic" - see: processWsoMessageRequests + encodeWsoMessage
 *   
 * In the present implementation there is also a WsoConnectionManager object involved
 * that holds a references to each established handler - resp. client connection. 
 *  
 * The "business logic" is implemented via an instance of the WsoMessageProcessor Interface
 * associated with one WebSocket-connection-path (resp. handler) supporting n client connections.
 *
 *</pre>
 */
public class WebSocketProvider implements ContentProvider {

    // default websocket connection url: "ws://host:port/wsoapi"
    public static final String DefaultPath = "/wsoapi";

    // RFC 6455 mandates SHA-1
    protected static final String WSO_HASH_FUNCTION = "SHA-1"; 

    protected static final String LS = System.lineSeparator();
    protected static final Logger LOG = LoggerFactory.getLogger(WebSocketProvider.class);
    protected WsoConnectionManager connectionManager = new WsoConnectionManager();

    // a empty default access controller
    protected WsoAccessController accessCtrl = new WsoAccessController() {

        @Override
        public boolean isSupportedPath(String path, StringBuilder msg) {
            if (connectionPathNames.contains(path)) {
                return true;
            }
            msg.append("Unsupported path [").append(path).append("]");
            return false;
        }

        @Override
        public boolean isAccessGranted(Map<String, String> requestAttributes, StringBuilder msg) {
            return true;
        }
    };

    protected Set<String> connectionPathNames = new HashSet<>();
    protected ProviderAdapter providerAdapter = new ProviderAdapter();
    // limit client -> server payload data size
    protected long maxUpStreamPayloadSize = 65000;

    /**
     */
    public WebSocketProvider() {
        addConnectionPath(DefaultPath);
    }

    /**
     * <pre>
     * WebSocket connections base on a one time, initial url path.
     * After a connection was established - there are NO pathnames involved any more.
     * 
     * How ever - it can still be useful to have different namespaces
     * </pre>
     */
    public WebSocketProvider addConnectionPath(String path) {
        connectionPathNames.add(path);
        return this;
    }

    /**
     */
    public WebSocketProvider setMaxUpStreamPayloadSize(long size) {
        maxUpStreamPayloadSize = size;
        return this;
    }

    /**
     */
    public WebSocketProvider setAccessController(WsoAccessController accessCtrl) {
        this.accessCtrl = accessCtrl;
        return this;
    }

    /**
     */
    public void addMessageProcessor(WsoMessageProcessor processor, String... pathParts) {
        String path = (pathParts != null && pathParts.length == 1) ? pathParts[0] : DefaultPath;
        if (connectionPathNames.contains(path)) {
            connectionManager.addMessageProcessor(processor, path);
        } else {
            throw new UncheckedWebSocketException(
                    String.format("WebSocket Message Processor for unknown path [%s]", path));
        }
    }

    /**
     */
    public void sendMessageTo(String connectionId, byte[] message) {
        if (connectionManager.isConnectionAvailable(connectionId)) {
            connectionManager.sendMessageFor(connectionId, message);
        }
    }

    /**
     */
    public Set<String> getConnectionPathNames() {
        return connectionPathNames;
    }

    /**
     * The JamnServer.ContentProvider Interface method.
     */
    @Override
    public void handleContentProcessing(RequestMessage request, Socket socket, Map<String, String> comData)
            throws IOException {
        WebSocketHandler handler = new WebSocketHandler(request.getPath(), providerAdapter);
        handler.handleRequest(request, socket, comData);
    }

    @Override
    public void handleContentProcessing(RequestMessage request, ResponseMessage response) {
        throw new UnsupportedOperationException(
                "WebSocket Content Provider requires use of extended (..., socket, comData) method");
    }

    /*********************************************************
     * <pre>
     * The Jamn WebSocket-Server implementations.
     * </pre>
     *********************************************************/
    /**
     * <pre>
     * The WsoConnectionManager holds the established connections to be identified by the ConnectionId.
     * 
     * A connection is represented by a WebSocketHandler=WsoConnection.
     * </pre>
     */
    private static class WsoConnectionManager {
        // connectionId -> connection
        protected Map<String, WsoConnection> openConnections = Collections.synchronizedMap(new HashMap<>());

        // path -> processor
        protected Map<String, WsoMessageProcessor> processorMap = Collections.synchronizedMap(new HashMap<>());

        /**
         */
        protected synchronized void connectionEstablished(String connectionId, WsoConnection connection) {
            openConnections.put(connectionId, connection);
        }

        /**
         */
        protected synchronized void connectionClosed(String connectionId) {
            openConnections.remove(connectionId);
            LOG.info("Closed WebSocket connection [{}]", connectionId);
        }

        /**
         * <pre>
         * This method is called for every incoming client "message" read from a WebSocketConnection.
         * </pre>
         */
        protected void processMessageFor(String connectionId, byte[] message) {
            WsoMessageProcessor processor;
            WsoConnection connection = openConnections.getOrDefault(connectionId, null);

            if (connection != null) {
                processor = processorMap.getOrDefault(connection.getPath(), null);
                if (processor != null) {
                    processor.onMessage(message, connection);
                }
            }
        }

        /**
         * <pre>
         * </pre>
         */
        protected boolean handleConnectionError(String connectionId, byte[] messageFragment, Exception error) {
            WsoMessageProcessor processor;
            WsoConnection connection = openConnections.getOrDefault(connectionId, null);

            if (connection != null) {
                processor = processorMap.getOrDefault(connection.getPath(), null);
                if (processor != null) {
                    return processor.onConnectionError(messageFragment, connection, error);
                }
            }
            return false; //false = stop socket
        }

        /**
         * A WebSocket is a connection established at one single access-point-path but
         * shared by all clients. Insofar is a WebSocket also associated with one
         * Processor that implements it's behavior.
         */
        protected void addMessageProcessor(WsoMessageProcessor processor, String path) {
            if (!processorMap.containsKey(path)) {
                processorMap.put(path, processor);
            } else {
                throw new UncheckedWebSocketException(
                        String.format("WebSocket Message Processor already defined for path [%s]", path));
            }
        }

        /**
         * The method implements the way from the WebSocket server side - back to a
         * connected client.
         */
        protected void sendMessageFor(String connectionId, byte[] message) {
            WsoConnection connection = openConnections.getOrDefault(connectionId, null);
            if (connection != null) {
                connection.sendMessage(message);
            }
        }

        /**
         */
        protected boolean isConnectionAvailable(String connectionId) {
            return openConnections.containsKey(connectionId);
        }

    }

    /**
     */
    public static class WebSocketConnectionRejectedException extends Exception {
        private static final long serialVersionUID = 1L;

        WebSocketConnectionRejectedException(String msg) {
            super(msg);
        }
    }

    /**
    */
    protected static class FatalWsoException extends IOException {
        private static final long serialVersionUID = 1L;

        protected FatalWsoException(String msg) {
            super(msg);
        }

        protected FatalWsoException(String msg, Exception cause) {
            super(msg, cause);
        }
    }

    /**
     */
    public static class UncheckedWebSocketException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedWebSocketException(String msg) {
            super(msg);
        }

        UncheckedWebSocketException(String msg, Exception cause) {
            super(msg, cause);
        }
    }

    /**
     */
    protected class ProviderAdapter {
        protected WsoConnectionManager getWsoConnectionManager() {
            return connectionManager;
        }

        protected WsoAccessController getWsoAccessController() {
            return accessCtrl;
        }

        protected long getMaxUpStreamPayloadSize() {
            return maxUpStreamPayloadSize;
        }
    }

    /**
     * <pre>
     * The handler implements the wso protocol level.
     * It is responsible for 
     *  - doing the handshake
     *  - reading message bytes and create wso packet frames
     *  - encoding and sending message bytes
     * </pre>
     */
    protected static class WebSocketHandler implements WsoConnection {

        protected String connectionId = "";
        protected String initUrlPath = "";
        protected OutputStream outStream;
        protected WsoAccessController accessCtrl;
        protected WsoConnectionManager connectionManager;
        protected long maxUpStreamPayloadSize;

        protected WebSocketHandler() {
        }

        public WebSocketHandler(String initUrlPath, ProviderAdapter adapter) {
            this();
            this.initUrlPath = initUrlPath;
            accessCtrl = adapter.getWsoAccessController();
            connectionManager = adapter.getWsoConnectionManager();
            maxUpStreamPayloadSize = adapter.getMaxUpStreamPayloadSize();
        }

        /**
         */
        @Override
        public String geConnectiontId() {
            return connectionId;
        }

        @Override
        public String getPath() {
            return initUrlPath;
        }

        @Override
        public synchronized void sendMessage(byte[] message) {
            if (outStream != null) {
                try {
                    byte[] encodedBytes = encodeWsoMessage(message);

                    outStream.write(encodedBytes);
                    outStream.flush();
                } catch (IOException e) {
                    throw new UncheckedWebSocketException(String.format("WebSocket send message error: [%s]",
                            geConnectiontId()), e);
                }
            }
        }

        /**
         * Interface method for (default)request processor.
         */
        protected void handleRequest(RequestMessage request, Socket socket, Map<String, String> comData)
                throws IOException {

            outStream = socket.getOutputStream();
            // create a unique connectionId
            connectionId = initUrlPath + " - " + Integer.toHexString(socket.hashCode()) + "-"
                    + socket.toString();

            try {
                // check accessibility
                StringBuilder errorMsg = new StringBuilder();
                if (!accessCtrl.isSupportedPath(initUrlPath, errorMsg)
                        || !accessCtrl.isAccessGranted(request.header().getAttributes(), errorMsg)) {
                    throw new WebSocketConnectionRejectedException(
                            String.format("WebSocket connection rejected [%s] [%s] [%s]", getPath(), errorMsg,
                                    connectionId));
                }

                processWsoHandshake(connectionId, request, comData);

                // from here io is websocket specific
                // and NO longer bound to the http protocol

                // the processing blocks reading the InStream until connection is closed
                // every read is considered as a "message"
                // and is forwarded/published to the ConnectionManager for processing
                socket.setSoTimeout(0);
                processWsoMessageRequests(socket.getInputStream());

                // returning from reading inStream
                // means the stream returned -1, end of stream and closed
                outStream.flush();
                outStream.close();

            } catch (Exception e) {
                throw new UncheckedWebSocketException(String.format("WebSocket request handling error: [%s]",
                        connectionId), e);
            } finally {
                // remove connection from the ConnectionManager
                connectionManager.connectionClosed(connectionId);
                try {
                    socket.close();
                } catch (IOException e) {
                    LOG.error(String.format("Finally closing WebSocket failed [%s] [%s]", e.getMessage(),
                            connectionId), e);
                }
            }
        }

        /**
         */
        @SuppressWarnings("java:S4790") // SHA-1 is explicitly mandated by RFC 6455 Section 1.3 for WebSocket handshakes
        protected String createWebSocketAcceptKey(String requestKey)
                throws NoSuchAlgorithmException {
            String acceptKey = requestKey + HttpHeader.MAGIC_WEBSOCKET_GUID;
            byte[] sha1 = MessageDigest.getInstance(WSO_HASH_FUNCTION).digest(acceptKey.getBytes(StandardCharsets.UTF_8)); 
            acceptKey = Base64.getEncoder().encodeToString(sha1);
            return acceptKey;
        }

        /**
         */
        protected void processWsoHandshake(String connectionId, RequestMessage request,
                Map<String, String> comData)
                throws IOException, NoSuchAlgorithmException {

            ResponseMessage handshakeResponse = new ResponseMessage(outStream);
            handshakeResponse.header()
                    .setHttpVersion(Field.HTTP_1_1)
                    .setHttpStatus(Status.SC_101_SWITCH_PROTOCOLS)
                    .setConnection(FieldValue.UPGRADE)
                    .setUpgrade(FieldValue.WEBSOCKET)
                    .set(Field.SEC_WEBSOCKET_ACCEPT, createWebSocketAcceptKey(request.header().getWebSocketKey()));

            handshakeResponse
                    .addContextData(comData.get(JamnServer.SOCKET_IDTEXT))
                    .addContextData(connectionId)
                    .addContextData(comData.get(JamnServer.REQUEST_HEADER_TEXT));

            try {
                handshakeResponse.send();

                // register this connection at the WsoConnectionManager
                connectionManager.connectionEstablished(connectionId, this);

                LOG.info("WebSocket connection established [{}]", connectionId);
            } catch (Exception e) {
                handshakeResponse.header().setHttpStatus(Status.SC_500_INTERNAL_ERROR);
                handshakeResponse.send();
                throw e;
            }
        }

        /**
         * <pre>
         * The websocket listening loop.
         * The provider supports basic wso frames with
         *  - text messages
         *  - the close opcode
         *  - and fragmented messages
         *    starting fragmentation when the fin byte = false
         *    stopping fragmentation when the fin byte gets = true again
         * </pre>
         */
        protected void processWsoMessageRequests(InputStream dataInStream) throws IOException { //NOSONAR

            WsoFrame frame = new WsoFrame(WsoFrame.EmptyPacket);
            WsoFrame fragmentedFrame = null;
            byte[] packet;
            int readPacketLength;
            boolean run = true;

            while (run) {
                try {
                    packet = new byte[1024];
                    readPacketLength = dataInStream.read(packet);

                    frame = new WsoFrame(readPacketLength, packet);
                    frame.decodeHeader();
                    LOG.debug(frame.getDescription());

                    if (frame.hasOpcode(Opcode.CLOSE)) {
                        packet = encodeWsoMessage(packet);
                        outStream.write(packet);
                        outStream.flush();
                    } else {
                        // try to read all still missing message bytes
                        frame.completePayload(dataInStream, maxUpStreamPayloadSize);
                        if (frame.isFin()) {
                            if (fragmentedFrame != null) {
                                // stopping fragmentation
                                fragmentedFrame.addFragment(frame);
                                frame = fragmentedFrame;
                                fragmentedFrame = null;
                            }
                            // hand over a complete websocket message for processing
                            connectionManager.processMessageFor(connectionId, frame.getPayloadData());
                        } else {
                            // starting fragment processing
                            if (fragmentedFrame == null) {
                                fragmentedFrame = frame;
                            } else {
                                fragmentedFrame.addFragment(frame);
                            }
                        }
                    }
                } catch (Exception e) {
                    // returns FALSE for STOP connection - run=false
                    run = connectionManager.handleConnectionError(connectionId, frame.getAvailablePacketDataOnError(), e);
                }
            }
        }

        /**
         * Create a server->client websocket frame package with message payload data.
         * No masking, always text.
         * inspired by works like
         * https://stackoverflow.com/questions/43163592/standalone-websocket-server-without-jee-application-server
         */
        protected byte[] encodeWsoMessage(byte[] messageData) {

            int payloadLen = messageData.length;
            int headerLen = 2; // minimum wso message 2 bytes
            byte[] headerBytes = new byte[10];

            // byte-1: - fin=true, opcode=text
            headerBytes[0] = (byte) (0b10000000 | (byte) Opcode.TEXT.getCode()); // => 10000001 = 0x81

            // byte-2: payload len
            if (payloadLen <= 125) {
                headerBytes[1] = (byte) payloadLen;
            } else if (payloadLen >= 126 && payloadLen <= 65535) {
                headerBytes[1] = (byte) 126;
                int len = payloadLen;
                headerBytes[2] = (byte) ((len >> 8) & ((byte) 255 & 0xff));
                headerBytes[3] = (byte) (len & (byte) 255);
                headerLen = 4;
            } else {
                headerBytes[1] = (byte) 127;
                // org - int len = rawData.length
                long len = payloadLen; // note an int is not big enough in java
                headerBytes[2] = (byte) ((len >> 56) & ((byte) 255 & 0xff));
                headerBytes[3] = (byte) ((len >> 48) & ((byte) 255 & 0xff));
                headerBytes[4] = (byte) ((len >> 40) & ((byte) 255 & 0xff));
                headerBytes[5] = (byte) ((len >> 32) & ((byte) 255 & 0xff));
                headerBytes[6] = (byte) ((len >> 24) & ((byte) 255 & 0xff));
                headerBytes[7] = (byte) ((len >> 16) & ((byte) 255 & 0xff));
                headerBytes[8] = (byte) ((len >> 8) & ((byte) 255 & 0xff));
                headerBytes[9] = (byte) (len & ((byte) 255 & 0xff));
                headerLen = 10;
            }

            int packetLength = headerLen + payloadLen;
            byte[] framePacket = new byte[packetLength];

            System.arraycopy(headerBytes, 0, framePacket, 0, headerLen);
            System.arraycopy(messageData, 0, framePacket, headerLen, payloadLen);

            return framePacket;
        }

        /**
         */
        protected enum Opcode {
            CONTINUATION(0x0),
            TEXT(0x1),
            BINARY(0x2),
            CLOSE(0x8),
            PING(0x9),
            PONG(0xA);

            private final int code;

            Opcode(int code) {
                this.code = code;
            }

            public int getCode() {
                return code;
            }

            public static Opcode fromCode(int code) {
                for (Opcode op : values()) {
                    if (op.getCode() == code) {
                        return op;
                    }
                }
                throw new IllegalArgumentException("Unknown opcode: " + code);
            }
        }

        /**
         * <pre>
         * A basic implementation of wso frame data structure.
         * Decoding and keeping wso header and payload byte data.
         * </pre>
         */
        protected static class WsoFrame {
            protected static final byte[] EmptyPacket = new byte[] { (byte) 0x81, 0x0 };

            private int readPacketLength;
            private byte[] packet;
            private byte[] maskingKey;
            private ByteArrayOutputStream fragments = null;

            private Opcode opcode;
            private boolean isMasked;
            private int payloadLength;
            private int headerOffset;

            public WsoFrame(byte[] dataPacket) throws IOException {
                this(dataPacket.length, dataPacket);
            }

            public WsoFrame(int readLen, byte[] dataPacket) throws IOException {
                // WS minimal frame = 2 bytes
                if (readLen < 2) {
                    throw new FatalWsoException(
                            String.format("Invalid frame: insufficient packet length [%s]", readLen));
                }
                packet = new byte[readLen];
                System.arraycopy(dataPacket, 0, packet, 0, readLen);
                readPacketLength = readLen;
            }

            /**
             */
            public boolean isFin() {
                // byte0 - bit 0
                return (packet[0] & 0b10000000) != 0;
            }

            /**
             */
            public boolean hasOpcode(Opcode code) {
                return opcode == code;
            }

            /**
             */
            public void decodeHeader() throws IOException {
                decodeRSV();
                decodeOpcode();
                decodeIsMasked();
                decodePayloadLength();
                decodeMask();
            }

            /**
             */
            protected void decodeRSV() {
                // byte0 - bit 1-3
                // RSV bits are ignored, could be checked: (byte0 & 0b01110000) != 0
            }

            /**
             */
            protected void decodeOpcode() {
                // byte0 - bit 4-7
                int value = packet[0] & 0b00001111;
                opcode = Opcode.fromCode(value);
            }

            /**
             */
            protected void decodeIsMasked() {
                // byte1 - bit 0
                isMasked = (packet[1] & 0b10000000) != 0;
            }

            /**
             */
            protected void decodePayloadLength() throws IOException {
                // byte1 - bit 1-7
                payloadLength = packet[1] & 0b01111111;

                // Start reading after the first two bytes
                headerOffset = 2;

                // Extended Payload Length (if any)
                if (payloadLength == 126) {
                    if (readPacketLength < 4) {
                        throw new FatalWsoException(
                                "Invalid frame: insufficient bytes for 16-bit payload length.");
                    }
                    payloadLength = ((packet[headerOffset] & 0xFF) << 8) | (packet[headerOffset + 1] & 0xFF);
                    headerOffset += 2;
                } else if (payloadLength == 127) {
                    if (readPacketLength < 10) {
                        throw new FatalWsoException(
                                "Invalid frame: insufficient bytes for 64-bit payload length.");
                    }
                    payloadLength = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLength = (payloadLength << 8) | (packet[headerOffset + i] & 0xFF);
                    }
                    headerOffset += 8;
                }
            }

            /**
             */
            protected void decodeMask() throws IOException {
                if (isMasked) {
                    if (packet.length < headerOffset + 4) {
                        throw new FatalWsoException("Invalid frame insufficient bytes for masking key.");
                    }
                    maskingKey = Arrays.copyOfRange(packet, headerOffset, headerOffset + 4);
                    headerOffset += 4;
                }
            }

            public String getDescription() {
                String ls = System.lineSeparator();
                StringBuilder builder = new StringBuilder(String.format("Wso Frame [%s]%s", this.hashCode(), ls));
                builder.append("Fin: ").append(this.isFin()).append(ls);
                builder.append("Opcode: ").append(this.opcode).append(ls);
                builder.append(String.format("Packet read len[%s], Head len[%s], Data len[%s]", readPacketLength,
                        headerOffset, readPacketLength - headerOffset)).append(ls);
                builder.append("Payload length: ").append(payloadLength).append(ls);
                if (hasFragments()) {
                    builder.append("Fragments size: ").append(fragments.size()).append(ls);
                }

                return builder.toString();
            }

            /**
             */
            public boolean hasFragments() {
                return (fragments != null && fragments.size() > 0);
            }

            /**
             */
            public void addFragment(WsoFrame frame) throws IOException {
                if (fragments == null) {
                    fragments = new ByteArrayOutputStream();
                }
                fragments.write(frame.getPayloadData());
            }

            /**
             */
            public void completePayload(InputStream dataInStream, long maxPayloadSize) throws IOException {

                int totalPacketLength = headerOffset + payloadLength;
                int remainingBytes = totalPacketLength - readPacketLength;
                int readLen = 0;
                int attempts = 0;
                byte[] buffer;

                if (totalPacketLength > maxPayloadSize) {
                    throw new FatalWsoException(
                            String.format("Max message size exceeded: [%s] > [%s]", totalPacketLength,
                                    maxPayloadSize));
                }
                if (remainingBytes > 0) {
                    buffer = new byte[totalPacketLength];
                    System.arraycopy(packet, 0, buffer, 0, readPacketLength);

                    while (readLen != -1 && readLen < remainingBytes) {
                        attempts++;
                        readLen += dataInStream.read(buffer, readPacketLength + readLen, remainingBytes - readLen);
                    }

                    if (readLen == remainingBytes && buffer.length == totalPacketLength) {
                        readPacketLength = totalPacketLength;
                        packet = buffer;
                    } else {
                        throw new FatalWsoException(String.format(
                                "Content differnce: readLen[%s], remainingBytes[%s], buffer.length[%s], totalPacketLength[%s], attempts[%s]%s",
                                readLen, remainingBytes, buffer.length, totalPacketLength, attempts,
                                System.lineSeparator() + this.getDescription()));
                    }
                }
            }

            /**
             */
            public byte[] getPayloadData() throws IOException {
                if (packet.length < headerOffset + payloadLength) {
                    throw new FatalWsoException(
                            String.format("Invalid wso packet: len[%s] < headerOffset[%s] + payloadLength[%s]",
                                    packet.length, headerOffset, payloadLength));
                }

                byte[] payloadData = new byte[payloadLength];
                System.arraycopy(packet, headerOffset, payloadData, 0, payloadLength);

                // Unmask payload
                if (isMasked) {
                    for (int i = 0; i < payloadData.length; i++) {
                        payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
                    }
                }

                if (hasFragments()) {
                    byte[] fragmentBytes = fragments.toByteArray();
                    byte[] buffer = new byte[payloadData.length + fragmentBytes.length];
                    System.arraycopy(payloadData, 0, buffer, 0, payloadData.length);
                    System.arraycopy(fragmentBytes, 0, buffer, payloadData.length, fragmentBytes.length);
                    payloadData = buffer;
                }

                return payloadData;
            }

            /**
             * <pre>
             * Try to get the first chunk of already read package bytes
             * to get some more informations for error handling.
             * </pre>
             */
            public byte[] getAvailablePacketDataOnError() {

                byte[] payloadData = new byte[packet.length];
                try {
                    System.arraycopy(packet, headerOffset, payloadData, 0, packet.length - headerOffset);

                    // Unmask payload
                    if (isMasked) {
                        for (int i = 0; i < payloadData.length; i++) {
                            payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
                        }
                    }
                } catch (Exception _) {
                    // nothing to do
                }

                return payloadData;
            }

        }
    }

    /**
     * <pre>
     * The WsoMessageProcessor interface defines a wso message listener.
     * </pre>
     */
    public static interface WsoMessageProcessor {

        /**
         * The wso message processing method
         * This method should NOT throw any exception - since this will cancel the the connection
         */
        public void onMessage(byte[] message, WsoConnection connection);

        /**
         * <pre>
         * experimental
         * default is - to stop the socket listening loop on any exception
         * </pre>
         */
        public default boolean onConnectionError(byte[] message, WsoConnection connection, Exception exp){
            LOG.error("WebSocket Connection ERROR:", exp);
            return false; //false = stop socket listening
        }

    }

    /**
     * <pre>
     * </pre>
     */
    public static interface WsoConnection {

        /**
         */
        public String geConnectiontId();

        /**
         * The initial url connection path.
         */
        public String getPath();

        /**
         * Send data to the client that established the connection.
         */
        public void sendMessage(byte[] message);

    }

    /**
     * <pre>
     * A rudimentary "security" interface.
     * </pre>
     */
    public static interface WsoAccessController {
        /**
         */
        public boolean isSupportedPath(String path, StringBuilder msg);

        /**
         */
        public boolean isAccessGranted(Map<String, String> requestAttributes, StringBuilder msg);

    }

}
