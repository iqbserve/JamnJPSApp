/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import iqb.jps.core.AppConfig;
import iqb.jps.core.HelperTool;
import iqb.jps.appcomp.JavaScriptProvider.JSCallContext;
import iqb.jps.appcomp.JavaScriptProvider.JavaScriptHostApp;
import iqb.jps.appcomp.JavaScriptProvider.JavaScriptHostAppAdapter;
import iqb.jps.appcomp.OperatingSystemInterface.CmdDef;

/**
 * <pre>
 * Example of a JavaScript Host App Adapter Interface. 
 * 
 * The adapter is used as a mediator between the Application and the JavaScript Provider.
 * It provides the JavaScript-HostApp implementation that is visible in js scripts.
 * 
 * The general cardinality is: 1-JsCall => 1-callContext - 1-hostApp - 1-jsEvalContext
 * </pre>
 */
public class JavaScriptAppAdapter implements JavaScriptHostAppAdapter {

    protected JavaScriptProvider javaScript;
    protected OperatingSystemInterface osIFace;
    protected Path appHome;
    protected AppConfig appConfig;

    /**
     */
    public JavaScriptAppAdapter(JavaScriptProvider javaScript, OperatingSystemInterface osIFace, AppConfig appConfig,
            Path appHome) {
        this.javaScript = javaScript;

        this.appHome = appHome;
        this.appConfig = appConfig;
        this.osIFace = osIFace;
    }

    /*******************************************************************************
     * Public interface
     *******************************************************************************/

    /**
     * Create HostApp instances to be injected in the JavaScript evaluation context.
     */
    @Override
    public JavaScriptHostApp newHostApp(JSCallContext callCtx) {
        return new HostApp(callCtx);
    }

    /*******************************************************************************
     * Host App implementation
     *******************************************************************************/
    /**
     * <pre>
     * The host app object is the app specific 
     * global visible Java-Object in a JavaScript.
     * </pre>
     */
    protected class HostApp implements JavaScriptHostApp {
        protected JSCallContext callCtx;

        protected HostApp(JSCallContext callCtx) {
            this.callCtx = callCtx;
        }

        @Override
        public String ls() {
            return System.lineSeparator();
        }

        /**
         */
        @Override
        public boolean isOnUnix() {
            return OperatingSystemInterface.IsOnUnix;
        }

        /**
         */
        @Override
        public void echo(String text) {
            if (callCtx.getOutputConsumer() != null) {
                callCtx.getOutputConsumer().accept(text);
            }
        }

        /**
         */
        @Override
        public String path(String path, String... parts) {
            return Paths.get(path, parts).toString();
        }

        /**
         */
        @Override
        public String homePath(String... parts) {
            return Paths.get(appHome.toString(), parts).toString();
        }

        /**
         */
        @Override
        public String workspacePath(String... parts) {
            return Paths.get(homePath(appConfig.getWorkspaceRoot()), parts).toString();
        }

        /**
         * <pre>
         * This method implements the host app specific interface to shell processes.
         * It is ONLY called from inside a JS-Script.
         * 
         * The ScriptOutputConsumer is a JS script call back method 
         * to directly forward shell output back to the calling script.
         * 
         * The callCtx also provides a outputConsumer
         * to forward the shell output to the java caller of the script.
         * 
         * The method itself returns output ONLY - if NO ScriptOutputConsumer is defined.
         * </pre>
         */
        @Override
        public List<String> shellCmd(String cmdLine, String workingDir, Consumer<String> outputConsumer,
                Map<String, String> envVars) {

            List<String> result = new ArrayList<>();
            Consumer<String> resultConsumer = result::add;

            CmdDef cmd = new CmdDef()
                    .setCmdParts(HelperTool.getInstance().rebuildQuotedWhitespaceStrings(cmdLine.split(" ")))
                    .setInherit(false)
                    .setWorkingDir(workingDir)
                    .setOutputConsumer(output -> {
                        if (outputConsumer != null) {
                            outputConsumer.accept(output);
                        } else {
                            resultConsumer.accept(output);
                        }

                        if (callCtx.getOutputConsumer() != null) {
                            callCtx.getOutputConsumer().accept(output);
                        }
                    })
                    .setEnvVars(envVars);
 
            osIFace.fnc().shellCmd(cmd);
            return result;
        }
    }

    /*******************************************************************************
     * Internals
     *******************************************************************************/

    /**
     */
    protected String runScript(String fileName, String... args) {
        Object result = javaScript.run(fileName, args);
        return result != null ? result.toString() : "";
    }

    /*******************************************************************************
     *******************************************************************************/
    /**
     */
    public static class UncheckedJavaScriptHostException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJavaScriptHostException(String msg) {
            super(msg);
        }

        public UncheckedJavaScriptHostException(String msg, Exception cause) {
            super(msg, cause);
        }
    }

}
