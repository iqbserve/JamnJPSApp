/* Authored by iqbserve.de */
package iqb.jps.wsoapi.base;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import iqb.jps.srvcomp.WebSocketProvider.WsoConnection;

public class WsoContext {

    WsoConnection connection;
    ExecutorService taskExecutor;
    Function<WsoCommonMessage, byte[]> newResponseMessage;

    /**
     */
    public WsoContext(ExecutorService taskExecutor, Function<WsoCommonMessage, byte[]> newResponseMessage, WsoConnection connection) {
        this.taskExecutor = taskExecutor;
        this.newResponseMessage = newResponseMessage;
        this.connection = connection;
    }
    
    /**
     */
    public void sendMessage(WsoCommonMessage message) {
        connection.sendMessage(newResponseMessage.apply(message));
    }

    /**
     */
    public ExecutorService getTaskExecutor() {
        return taskExecutor;
    }
}
