/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import iqb.jps.core.AppConfig;

/**
 * <pre>
 * The class provides a rudimentary abstraction for operating system specifics.
 * The main Interface is "OSFunctions" accessible via the fnc() method.
 * 
 * The main functionality is a "ShellProcess" wrapper and a "shellCmd" method.
 * </pre>
 */
public class OperatingSystemInterface {

    public static final boolean IsOnWindows;
    public static final boolean IsOnUnix;
    static {
        String name = System.getProperty("os.name").toLowerCase();
        IsOnWindows = name.contains("win");
        IsOnUnix = (name.contains("nix") || name.contains("nux") || name.contains("aix"));
    }

    protected OSFunctions osFunctions;
    protected OSIFaceSecurityController securityCtrl = new OSIFaceSecurityController() {
    };

    protected Charset shellEncoding = StandardCharsets.UTF_8;
    protected Path homePath = Paths.get(System.getProperty("user.dir"));

    /**
     */
    public OperatingSystemInterface(AppConfig config, Path homePath) {
        this.homePath = homePath;
        if (IsOnWindows) {
            shellEncoding = Charset.forName(config.getWinShellEncoding());
            osFunctions = new WinowsFunctions();
        } else if (IsOnUnix) {
            shellEncoding = Charset.forName(config.getUnixShellEncoding());
            osFunctions = new UnixFunctions();
        }
    }

    /**
     */
    public OSFunctions fnc() {
        return osFunctions;
    }

    /**
     * The function interface
     */
    public static interface OSFunctions {

        /**
         */
        public List<String> shellCmd(CmdDef cmd);

    }

    /**
     */
    public static interface OSIFaceSecurityController {
        default boolean isPathAccessAlowed(String path) {
            return true;
        }
    }

    /**
     */
    protected abstract class AbstractOSFunctions implements OSFunctions {

        protected AbstractOSFunctions() {
        }

        @Override
        public List<String> shellCmd(CmdDef cmd) {
            ShellProcess process = new ShellProcess()
                    .setCommand(cmd);
            process.start();
            return process.getOutput();
        }
    }

    /**
     */
    public static interface ShellProcessListener {
        /**
         */
        void onShellClosed(String id);
    }

    /**
     */
    public class ShellProcess {
        protected String id = "";
        protected ShellProcessListener listener = id -> {
        };

        protected CmdDef cmd = null;

        protected Process process = null;
        protected List<String> outPut = new ArrayList<>();

        protected ShellProcess() {
        }

        public ShellProcess(String id) {
            this.id = id;
        }

        /**
         */
        public ShellProcess setCommand(CmdDef cmd) {
            this.cmd = cmd;
            return this;
        }

        /**
         */
        public ShellProcess setListener(ShellProcessListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         */
        public Process getProcess() {
            return process;
        }

        /**
         */
        public List<String> getOutput() {
            return new ArrayList<>(this.outPut);
        }

        /**
         */
        public String getId() {
            return id;
        }

        /**
         */
        public void start() {
            String line = "";
            ProcessBuilder builder = null;

            try {

                builder = new ProcessBuilder();
                builder.command(cmd.getCommandAsList());
                builder.environment().putAll(cmd.getEnvVars());
                builder.redirectErrorStream(true);
                if (cmd.isInherit()) {
                    builder.inheritIO();
                }

                if (cmd.getWorkingDir() != null && !cmd.getWorkingDir().isEmpty()) {
                    Path path = Paths.get(resolveWorkingDir(cmd.getWorkingDir()));
                    builder.directory(path.toFile());
                }
                process = builder.start();

                try (BufferedReader stdInput = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), shellEncoding));) {

                    Consumer<String> outputConsumer = cmd.getOutputConsumer();
                    while ((line = stdInput.readLine()) != null) {
                        if (outputConsumer != null) {
                            outputConsumer.accept(line);
                        } else {
                            outPut.add(line);
                        }
                    }
                }

                process.waitFor();

            } catch (InterruptedException | IOException e) { //NOSONAR
                throw new UncheckedOSIFaceException(
                        String.format("ERROR executing ShellProcess [%s] [%s]",
                                String.join(" ", cmd.getCommandAsList()),
                                e.getMessage()),
                        e);
            } finally {
                close();
            }
        }

        /**
         */
        public void close() {
            if (process != null) {
                process.destroy();
                process.destroyForcibly();
            }
            if (listener != null) {
                listener.onShellClosed(id);
            }
        }
    }

    /**
     */
    protected String resolveWorkingDir(String path) {
        if (path == null || path.isEmpty()) {
            path = homePath.toString();
        }

        if (!securityCtrl.isPathAccessAlowed(path)) {
            throw new UncheckedOSIFaceException(String.format("Path Access denied [%s]", path));
        }
        return path;
    }

    /**
     */
    protected class WinowsFunctions extends AbstractOSFunctions {
        protected WinowsFunctions() {
            super();
        }
    }

    /**
     */
    protected class UnixFunctions extends AbstractOSFunctions {
        protected UnixFunctions() {
            super();
        }
    }

    /**
     */
    public static class CmdDef {
        private String[] cmdParts = new String[] {};
        private String workingDir = "";
        private boolean inherit = false;
        private Consumer<String> outputConsumer = null;
        private Map<String, String> envVars = new HashMap<>();

        public List<String> getCommandAsList() {
            List<String> commandList = new ArrayList<>(Arrays.asList(cmdParts));
            if (IsOnWindows) {
                commandList.add(0, "cmd");
                commandList.add(1, "/c");
            }
            return commandList;
        }

        public String[] getCmdParts() {
            return cmdParts;
        }

        public CmdDef setCmdParts(String[] cmdParts) {
            this.cmdParts = cmdParts;
            return this;
        }

        public String getWorkingDir() {
            return workingDir;
        }

        public CmdDef setWorkingDir(String workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        public boolean isInherit() {
            return inherit;
        }

        public CmdDef setInherit(boolean inherit) {
            this.inherit = inherit;
            return this;
        }

        public boolean hasOutputConsumer() {
            return outputConsumer != null;
        }

        public Consumer<String> getOutputConsumer() {
            return outputConsumer;
        }

        public CmdDef setOutputConsumer(Consumer<String> outputConsumer) {
            this.outputConsumer = outputConsumer;
            return this;
        }

        public Map<String, String> getEnvVars() {
            return envVars;
        }

        public CmdDef setEnvVars(Map<String, String> envVars) {
            this.envVars = envVars;
            return this;
        }
    }

    /**
     */
    public static class UncheckedOSIFaceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedOSIFaceException(String message) {
            super(message);
        }

        public UncheckedOSIFaceException(String message, Exception cause) {
            super(message, cause);
        }
    }
}
