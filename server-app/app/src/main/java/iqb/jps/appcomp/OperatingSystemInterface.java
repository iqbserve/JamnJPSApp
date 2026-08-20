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
import java.util.List;
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

    protected static boolean Windows = true;
    protected static boolean Unix = false;
    static {
        String name = System.getProperty("os.name").toLowerCase();
        Windows = name.contains("win");
        Unix = (name.contains("nix") || name.contains("nux") || name.contains("aix"));
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
        if (Windows) {
            shellEncoding = Charset.forName(config.getWinShellEncoding());
            osFunctions = new WinowsFunctions();
        } else if (Unix) {
            shellEncoding = Charset.forName(config.getUnixShellEncoding());
            osFunctions = new UnixFunctions();
        }
    }

    /**
     */
    public boolean isOnWindows() {
        return Windows;
    }

    /**
     */
    public boolean isOnUnix() {
        return Unix;
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
        public List<String> shellCmd(String[] cmdParts, String workingDir, boolean inherit,
                Consumer<String> outputConsumer);

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
        public List<String> shellCmd(String[] cmdParts, String workingDir, boolean inherit,
                Consumer<String> outputConsumer) {
            ShellProcess process = new ShellProcess()
                    .setCommand(cmdParts)
                    .setWorkingDir(workingDir)
                    .setInherit(inherit)
                    .setOutputConsumer(outputConsumer);
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
        protected Consumer<String> outputConsumer = null;
        protected List<String> command;
        protected String workingDir;
        protected boolean inherit = false;

        protected Process process = null;
        protected List<String> outPut = new ArrayList<>();

        protected ShellProcess() {
        }

        public ShellProcess(String id) {
            this.id = id;
        }

        /**
         */
        public ShellProcess setCommand(String[] cmdParts) {
            command = new ArrayList<>();

            if (Windows) {
                command.add(0, "cmd");
                command.add(1, "/c");
            }
            command.addAll(Arrays.asList(cmdParts));
            return this;
        }

        /**
         */
        public ShellProcess setWorkingDir(String workingDir) {
            this.workingDir = resolveWorkingDir(workingDir);
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
        public ShellProcess setOutputConsumer(Consumer<String> outputConsumer) {
            this.outputConsumer = outputConsumer;
            return this;
        }

        /**
         */
        public ShellProcess setInherit(boolean inherit) {
            this.inherit = inherit;
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
                builder.command(command);
                builder.redirectErrorStream(true);
                if (inherit) {
                    builder.inheritIO();
                }

                if (workingDir != null && !workingDir.isEmpty()) {
                    Path path = Paths.get(workingDir);
                    builder.directory(path.toFile());
                }
                process = builder.start();

                try (BufferedReader stdInput = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), shellEncoding));) {

                    while ((line = stdInput.readLine()) != null) {
                        if (outputConsumer != null) {
                            outputConsumer.accept(line);
                        } else {
                            outPut.add(line);
                        }
                    }
                }

                process.waitFor();

            } catch (InterruptedException | IOException e) {
                throw new UncheckedOSIFaceException(
                        String.format("ERROR executing ShellProcess [%s] [%s]", String.join(" ", command),
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
