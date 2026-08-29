/* Authored by iqbserve.de */
package iqb.jps.extapi;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Consumer;

import iqb.jps.core.AppConfig;
import iqb.jps.core.JsonTool;

/**
 * Context provided to an instance of an extension.
 * Contains common resources and configuration needed by the extension.
 */
public class ExtensionInstanceContext {
    private final Consumer<String> outputConsumer;
    private final Path dataPath;
    private final JsonTool jsonTool;
    private final AppConfig appConfig;
    private final Charset encoding;

    public ExtensionInstanceContext(
            Consumer<String> outputConsumer,
            Path dataPath,
            JsonTool jsonTool,
            AppConfig appConfig,
            Charset encoding) {
        this.outputConsumer = outputConsumer;
        this.dataPath = dataPath;
        this.jsonTool = jsonTool;
        this.appConfig = appConfig;
        this.encoding = encoding;
    }

    public JsonTool getJsonTool() {
        return jsonTool;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public Path getDataPath() {
        return dataPath;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public Consumer<String> getOutputConsumer() {
        return outputConsumer;
    }
}