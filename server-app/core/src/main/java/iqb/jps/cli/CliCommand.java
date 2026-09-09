package iqb.jps.cli;

import java.util.function.BiFunction;

/**
 * <pre>
 * A command object implementation providing a unique name and a execution function.
 * The generic part defines the call context type passed to the command for execution.
 * </pre>
 */
public class CliCommand <T> {

    // command constants to represent a standard unknown and exit command
    // nothing more than a name, no functional behavior beyond that
    private static final CliCommand<Object> UNKNOWN_COMMAND = new CliCommand<>("Unknown", (args, ctx) -> "Unknown command");
    private static final CliCommand<Object> EXIT_COMMAND = new CliCommand<>("exit", (args, ctx) -> "exit");

    @SuppressWarnings("unchecked")
    public static <T> CliCommand<T> unknownCommand() {
        return (CliCommand<T>) UNKNOWN_COMMAND;
    }
    @SuppressWarnings("unchecked")
    public static <T> CliCommand<T> exitCommand() {
        return (CliCommand<T>) EXIT_COMMAND;
    }

    public static final String ANSI_CODE_CLEAR_SCREEN = "\u001b[H\u001b[2J\u001b[3J";

    protected String name;
    protected BiFunction<CliCmdArgs, T, String> commandFunction;

    public CliCommand(String name, BiFunction<CliCmdArgs, T, String> commandFunction) {
        this.name = name;
        this.commandFunction = commandFunction;
    }

    /**
     * <pre>
     * Executes the command with the given arguments and context.
     * </pre>
     */
    public String execute(String[] args, T context) {
        return commandFunction.apply(new CliCmdArgs(args), context);
    }

    /**
     * The command key (name) used to identify this command.
     */
    public String getName() {
        return name;
    }
}