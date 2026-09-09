/* Authored by iqbserve.de */
package iqb.jps.appcomp;

/**
 * <pre>
 * The CliApp command execution context,
 * providing access to the server connection.
 * </pre>
 */
public class CmdCallContext  {

    private final ServerConnection serverConnection;
    public CmdCallContext(ServerConnection serverConnection){
        this.serverConnection = serverConnection;
    }

    public  ServerConnection getServerConnection() {
        return  serverConnection;
    }   
}
