/* Authored by iqbserve.de */
package iqb.jps.cli;

import iqb.jps.core.HelperTool;

/**
 * <pre>
 * Represents a parsed command line, including the command name and its arguments.
 * </pre>
 */
public class CliCommandLine {
    /**
     * Parses a command line string into an array of token.
     */
    public static String[] parseCommandLine(String text) {
        return HelperTool.getInstance().parseCommandLine(text);
    }

    private String[] tokens = null;
    private String commandName = "";
    private String[] args = {};

    /**
     * Constructs a CliCommandLine object by parsing the given source string.
     */
    public CliCommandLine(String source) {
        if(source !=null && !source.isBlank()) {
            this.tokens = parseCommandLine(source);
            if (tokens.length >= 1) {
                this.commandName = tokens[0];
            }
            if (tokens.length >= 2) {
                this.args = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, this.args, 0, this.args.length);
            }
        }
    }
    public boolean isUseable() {
        return tokens != null && tokens.length > 0;
    }
    public boolean isCommand(String name) {
        return this.commandName.equalsIgnoreCase(name);
    }
    public String[] getTokens() {
        return tokens;
    }
    public String getCommandName() {
        return commandName;
    }
    public String[] getArgs() {
        return args;
    }
}