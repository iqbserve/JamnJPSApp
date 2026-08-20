/* Authored by iqbserve.de */
package iqb.jps.wsoapi.base;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.core.AppConfig;
import iqb.jps.core.JsonTool;
import iqb.jps.srvcomp.WebSocketProvider.WsoConnection;
import iqb.jps.srvcomp.WebSocketProvider.WsoMessageProcessor;

public class WsoMessageHandler implements WsoMessageProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(WsoMessageHandler.class);

    protected final JsonTool json;
    protected final AppConfig appConfig;
    protected Set<TaskCartridge> messageProcessors = new CopyOnWriteArraySet<>();

    // websocket messages are always utf8 encoded by spec
    protected Charset encoding = StandardCharsets.UTF_8;

    protected final ExecutorService taskExecutor;

    public WsoMessageHandler(JsonTool jsonTool, AppConfig appConfig) {
        this.json = jsonTool;
        this.appConfig = appConfig;
        taskExecutor = Executors.newFixedThreadPool(appConfig.getWebSocketTaskWorkerNumber());
    }

    /**
     * Handle an incoming WebSocket message.
     */
    @Override
    public void onMessage(byte[] messageBytes, WsoConnection connection) {
        WsoCommonMessage wsoRequest = newRequestMessage(messageBytes);
        TaskCartridge cart = findResponsibleProcessorFor(wsoRequest);
        if (cart != null) {
            cart.context().setConnection(connection);
            cart.processor().processWsoMessage(wsoRequest, cart.context());
        } else {
            throw new UncheckedWsoProcessingException("No WSO message processor defined for message: " + wsoRequest);
        }
    }

    /**
     */
    public WsoMessageHandler addTaskProcessor(WsoTaskProcessor processor) {
        TaskCartridge cart = new TaskCartridge(processor, new WsoContext(taskExecutor, this::newResponseMessage));
        messageProcessors.add(cart);
        return this;
    }

    /**
     * Create a WsoCommonMessage from the received byte array message.
     */
    protected WsoCommonMessage newRequestMessage(byte[] messageBytes) {
        String textMessage = new String(messageBytes, encoding);
        return json.toObject(textMessage, WsoCommonMessage.class);
    }

    /**
     */
    protected byte[] newResponseMessage(WsoCommonMessage message) {
        return json.toString(message).getBytes(encoding);
    }

    /**
     */
    protected TaskCartridge findResponsibleProcessorFor(WsoCommonMessage message) {
        for (TaskCartridge cart : messageProcessors) {
            if (cart.processor().isResponsibleFor(message)) {
                return cart;
            }
        }
        return null;
    }

    /**
     */
    protected record TaskCartridge(WsoTaskProcessor processor, WsoContext context) {
    }
}
