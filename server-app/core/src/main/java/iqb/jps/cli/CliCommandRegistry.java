/* Authored by iqbserve.de */
package iqb.jps.cli;

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 * A registry for CLI commands, allowing adding and retrieving commands by name.
 * The generic part defines the call context type passed to the commands for execution.
 * </pre>
 */
public class CliCommandRegistry <T>{
   
    private Map<String, CliCommand<T>> cliCommands = new HashMap<>();

    public CliCommandRegistry() {
        // create a default help command
        // listing all registered commands
        addCommand(new CliCommand<T>("help", (cmdArgs, ctx) -> 
            String.format("Available commands: [%s]", String.join(", ", cliCommands.keySet()))
        ));
    }

    /**
     */
    public CliCommandRegistry<T> addCommand(CliCommand<T> command) {
        cliCommands.put(command.getName(), command);
        return this;
    }

    /**
     */
    public CliCommand<T> getCommand(String name) {
        return cliCommands.getOrDefault(name, CliCommand.unknownCommand());
    }
}
