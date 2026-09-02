/* Authored by iqbserve.de */
package iqb.jps.extapi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Consumer;

import iqb.jps.core.AppConfig;
import iqb.jps.core.HelperTool;
import iqb.jps.core.JsonTool;

/**
 * Context provided to an instance of an extension.
 * Contains common resources and configuration needed by the extension.
 */
public class ExtensionInstanceContext {
    private static final HelperTool Tool = HelperTool.getInstance();
    
    private final Consumer<String> outputConsumer;
    private final Path dataPath;
    private final JsonTool jsonTool;
    private final AppConfig appConfig;
    private final ExtensionWebAppConfigurator webAppConfigurator;
    private final Charset encoding;

    public ExtensionInstanceContext(
            Consumer<String> outputConsumer,
            Path dataPath,
            JsonTool jsonTool,
            AppConfig appConfig,
            Charset encoding,
            ExtensionWebAppConfigurator webAppConfigurator) {
        this.outputConsumer = outputConsumer;
        this.dataPath = dataPath;
        this.jsonTool = jsonTool;
        this.appConfig = appConfig;
        this.encoding = encoding;
        this.webAppConfigurator = webAppConfigurator;
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

    public ExtensionWebAppConfigurator getWebAppConfigurator() {
        return webAppConfigurator;
    }

    public String readStringFrom(Class<?> clazz, String resourcePath) throws IOException {
        return Tool.readStringResourceFrom(clazz, resourcePath);
    }

    public byte[] readResourceFrom(Class<?> clazz, String resourcePath) throws IOException {
        return Tool.readResourceFrom(clazz, resourcePath);
    }
}