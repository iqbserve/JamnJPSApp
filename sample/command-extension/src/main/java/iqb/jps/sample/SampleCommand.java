/* Authored by iqbserve.de */
package iqb.jps.sample;

import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

/**
 * <pre>
 * A sample "command" extension 
 * with no dependencies to JPS classes (scope="").
 * </pre>
 */
public class SampleCommand {

    // the command name and the host output
    private String name; // the name is name of the json def file - NOT the class name
    private Consumer<String> hostOutput;

    // constructor with a standard host context map
    // providing only standard java objects
    @SuppressWarnings("unchecked")
    public SampleCommand(Map<String, Object> ctx) {
        name = (String) ctx.getOrDefault("name", "unknown");
        hostOutput = (Consumer<String>) ctx.get("output");
    }

    /**
     * The standard command execution method,
     * with a <String> (String[]) signature
     */
    public String execute(String[] args) {

        for (String arg : args) {
            if (arg.equalsIgnoreCase("-h")) {
                echo(String.join("\n", "\nCommand Help:",
                        "Sample command extension that echos given arguments if any."));
                return null;
            }
        }

        echo(String.format("Start: runext [%s] [%s]", name, new Date()));

        if (args.length > 0) {
            return "Echo args:" + "\n " + String.join("\n ", args);
        } else {
            echo("<no args>");
        }

        return null;
    }

    // a wrapper method for the host output consumer
    private void echo(Object val) {
        hostOutput.accept(val.toString());
    }
}
