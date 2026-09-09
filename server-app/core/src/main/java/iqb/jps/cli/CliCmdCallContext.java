package iqb.jps.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * <pre>
 * A context object for passing to a command execution function, providing handling functions.
 * Commands and a command registry must be typed to such a context type.
 * The context realizes the interface between a command and its execution environment.
 * </pre>
 */
public class CliCmdCallContext {
    protected BufferedReader in;
    protected PrintWriter out;

    /**
     */
    public CliCmdCallContext(BufferedReader in, PrintWriter out) {
        this.in = in;
        this.out = out;
    }

    /**
     */
    public String queryInput(String message) {
        print(message + ": ");
        return readLine();
    }

    /**
     */
    protected void print(String message) {
        out.print(message);
        out.flush();
    }

    /**
     */
    protected String readLine() {
        try {
            String input = in.readLine();
            return input != null ? input.trim() : "";
        } catch (Exception _) {
            return "";
        }
    }
}