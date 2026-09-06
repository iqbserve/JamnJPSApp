/* Authored by iqbserve.de */
package iqb.jps.cliapi;

import java.util.HashMap;
import java.util.Map;
import iqb.jps.cliapi.CliInterface.CliCommand;

public class CliCommandRegistry {

    private Map<String, CliCommand> cliCommands = new HashMap<>();

    private static final CliCommandRegistry instance = new CliCommandRegistry();
    public static CliCommandRegistry getInstance() {
        return instance;
    }

    private CliCommandRegistry() {
        // create a default help command
        // listing all registered commands
        addCommand(new CliCommand("help", (cmdArgs, ctx) -> {
            return String.format("Available commands: [%s]", String.join(", ", cliCommands.keySet()) + ", exit");
        }));
    }

    /**
     */
    public CliCommandRegistry addCommand(CliCommand command) {
        cliCommands.put(command.getName(), command);
        return this;
    }

    /**
     */
    public CliCommand getCommand(String name) {
        return cliCommands.getOrDefault(name, CliCommand.UnknownCommand);
    }
}
