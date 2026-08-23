/* Authored by iqbserve.de */
package iqb.jps.wsoapi.base;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.core.AppConfig;
import iqb.jps.core.JsonTool;
import iqb.jps.srvcomp.WebSocketProvider.WsoConnection;
import iqb.jps.srvcomp.WebSocketProvider.WsoMessageProcessor;

/**
 * <pre>
 * The Dispatcher forwards messages to the appropriate processor
 * and provides it with a context object for sending responses and starting tasks.
 * </pre>
 */
public class WsoMessageDispatcher implements WsoMessageProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(WsoMessageDispatcher.class);

    protected final JsonTool json;
    protected final AppConfig appConfig;
    protected Set<WsoTaskProcessor> messageProcessors = new CopyOnWriteArraySet<>();

    // websocket messages are always utf8 encoded by spec
    protected Charset encoding = StandardCharsets.UTF_8;

    protected ExecutorService taskExecutor;

    public WsoMessageDispatcher(JsonTool jsonTool, AppConfig appConfig) {
        this.json = jsonTool;
        this.appConfig = appConfig;
        taskExecutor = createTaskExecutor();
    }

    /**
     */
    protected ExecutorService createTaskExecutor() {
        int workers = appConfig.getWebSocketTaskWorker();
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Do the dispatching of incoming messages to the appropriate processor.
     */
    @Override
    public void onMessage(byte[] messageBytes, WsoConnection connection) {
        WsoCommonMessage wsoRequest = newRequestMessage(messageBytes);
        WsoTaskProcessor processor = findResponsibleProcessorFor(wsoRequest);
        if (processor != null) {
            WsoContext context = new WsoContext(taskExecutor, this::newResponseMessage, connection);
            processor.processWsoMessage(wsoRequest, context);
        } else {
            throw new UncheckedWsoProcessingException("No WSO message processor defined for message: " + wsoRequest);
        }
    }

    /**
     * Find the processor that is responsible for the given message.
     */
    protected WsoTaskProcessor findResponsibleProcessorFor(WsoCommonMessage message) {
        for (WsoTaskProcessor processor : messageProcessors) {
            if (processor.isResponsibleFor(message)) {
                return processor;
            }
        }
        return null;
    }

    /**
     * Create a WsoCommonMessage from the received byte array message.
     */
    protected WsoCommonMessage newRequestMessage(byte[] messageBytes) {
        String textMessage = new String(messageBytes, encoding);
        return json.toObject(textMessage, WsoCommonMessage.class);
    }

    /**
     * Create a byte array from a WsoCommonMessage to be sent back to the client.
     */
    protected byte[] newResponseMessage(WsoCommonMessage message) {
        return json.toString(message).getBytes(encoding);
    }

    /**
    */
    public WsoMessageDispatcher addTaskProcessor(WsoTaskProcessor processor) {
        messageProcessors.add(processor);
        return this;
    }

}
