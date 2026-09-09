package iqb.jps.cli;

/**
 * An argument object passed to the command execution function.
 */
public class CliCmdArgs {
    protected String[] args;

    public CliCmdArgs(String[] args) {
        this.args = args;
    }

    public String[] getArgsArray() {
        return args;
    }

    public String getArg(int index) {
        return args != null && index >= 0 && index < args.length ? args[index] : "";
    }

    public boolean hasArg(String name) {
        String propName = name + "=";
        for (String arg : args) {
            if (arg.equals(name) || arg.startsWith(propName)) {
                return true;
            }
        }
        return false;
    }

    public String getArgValue(String name) {
        String propName = name + "=";
        for (String arg : args) {
            if (arg.startsWith(propName)) {
                return arg.substring(propName.length());
            }
        }
        return null;
    }
}