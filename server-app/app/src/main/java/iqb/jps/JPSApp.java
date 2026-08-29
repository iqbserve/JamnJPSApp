/* Authored by iqbserve.de */
package iqb.jps;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.JamnServer.ContentProvider;
import iqb.jps.appcomp.ExtensionHandler;
import iqb.jps.appcomp.JavaScriptAppAdapter;
import iqb.jps.appcomp.JavaScriptProvider;
import iqb.jps.appcomp.OperatingSystemInterface;
import iqb.jps.boot.ApplicationLauncher;
import iqb.jps.core.AppConfig;
import iqb.jps.core.HelperTool;
import iqb.jps.core.JsonTool;
import iqb.jps.core.UncheckedAppException;
import iqb.jps.core.ResourceFileCache;
import iqb.jps.srvcomp.WebContentProvider;
import iqb.jps.srvcomp.WebContentProvider.WebFile;
import iqb.jps.srvcomp.WebServiceProvider;
import iqb.jps.srvcomp.WebServiceProvider.WebServiceDefinitionException;
import iqb.jps.srvcomp.WebSocketProvider;
import iqb.jps.webapi.WebAppConfigService;
import iqb.jps.wsoapi.RunExtensionTaskProcessor;
import iqb.jps.wsoapi.RunJavaScriptTaskProcessor;
import iqb.jps.wsoapi.base.WsoMessageDispatcher;

/**
 * <pre>
 * This class is defined as the Application main class (app.class.name).
 * The information comes from the build and is stored in a resource file (e.g. build.info.properties).
 * 
 * The callstack is:
 *  $> java -jar [executable].jar
 *    -> [launcher].main()
 *      -> {app.class.name}.main()
 * </pre>
 */
public class JPSApp {

    private JPSApp() {
        // private constructor to prevent instantiation
    }

    private static final JPSApp Instance = new JPSApp();

    private static final Logger LOG = LoggerFactory.getLogger(JPSApp.class);
    private static final HelperTool Tool = HelperTool.getInstance();

    private Properties buildProperties = new Properties();
    private String appName = "";
    private Path appHome = null;
    private AppConfig appConfig = null;
    private JsonTool jsonTool = JsonTool.Instance();
    private Charset standardEncoding = StandardCharsets.UTF_8;

    private OperatingSystemInterface osIFace = null;
    private JamnServer server = null;
    private WebContentProvider webContentProvider = null;
    private WebServiceProvider webServiceProvider = null;
    private WebSocketProvider webSocketProvider = null;
    private WsoMessageDispatcher wsoMessageDispatcher = null;
    private ExtensionHandler extensionHandler = null;

    private Optional<JavaScriptProvider> javaScript = Optional.empty();

    /**
     */
    public static void main(String[] args) {
        Instance.start(args);
    }

    /**
     */
    private void start(String[] args) {
        try {
            initialize(args);

            if (appConfig.hasAppProfile()) {
                server.start();
            } else {
                // for later use
            }
        } catch (Exception e) {
            throw new UncheckedAppException(appName + " Initialization error", e);
        }

        LOG.info(getStartInfo());
    }

    /**
     */
    private String getStartInfo() {
        String crlf = "\n # ";
        return new StringBuilder("#")
                .append(crlf)
                .append(appName).append(" STARTED - Home [").append(appHome).append("]").append(crlf)
                .toString();
    }

    /**
     */
    private void initialize(String[] args) throws IOException, WebServiceDefinitionException, URISyntaxException {
        buildProperties.load(getClass().getResourceAsStream(ApplicationLauncher.BUILD_INFO_PROPERTIES));
        appName = buildProperties.getProperty("appname");
        appHome = Paths.get(System.getProperty("user.dir"));
        Map<String, String> argsMap = Tool.argsToConfigValues(args);

        initLogging();
        initConfig(argsMap);
        osIFace = new OperatingSystemInterface(appConfig, appHome);

        initExtensionHandler();
        initJavaScriptProvider();

        initServer();
        initContentProvider();
        initWebServiceProvider();
        initWebSocketProvider();

        initAppServicesAndObjects();

    }

    /**
     */
    private void initLogging() throws IOException {
        InputStream inputStream;
        String fileName = "logging.properties";
        Path filePath = getHomePath("jps." + fileName);
        String source = "";

        if (Files.exists(filePath)) {
            inputStream = Files.newInputStream(filePath);
            source = filePath.toString();
        } else {
            fileName = "/app." + fileName;
            inputStream = getClass().getResourceAsStream(fileName);
            source = fileName;
        }
        if (inputStream != null) {
            LogManager.getLogManager().readConfiguration(inputStream);
            LOG.info("Logging config read from: [{}]", source);
        } else {
            LOG.warn("NO Logging config available");
        }
    }

    /**
     */
    private void initConfig(Map<String, String> argsMap) throws IOException {
        InputStream inputStream = null;
        Path filePath = getHomePath("jps.properties");
        String defaultConfigSrc = AppConfig.getDefaultConfig(appName);

        if (Files.exists(filePath)) {
            inputStream = Files.newInputStream(filePath);
            appConfig = new AppConfig(inputStream);
            // put all user config properties into the base config, overwriting defaults
            LOG.info("Application config read from: [{}]", filePath);
        } else {
            // just use the default base config and write to file for user to edit
            appConfig = new AppConfig(new StringReader(defaultConfigSrc));
            Files.writeString(filePath, defaultConfigSrc, StandardOpenOption.CREATE);
            LOG.warn("NO Application config found. Created Default config: [{}]", filePath);
        }

        // take over build properties
        Tool.getPropertiesFrom(buildProperties, new String[] { "jps.javascript.enabled" }, appConfig.getProperties());
        appConfig.getBuildProperties().putAll(buildProperties);
        appConfig.getProperties().putAll(argsMap);

        standardEncoding = Charset.forName(appConfig.getStandardEncoding());

        Tool.getPropertiesFrom(appConfig.getProperties(),
                new String[] { "jps.shutdown.warnings.enabled" },
                System.getProperties());
    }

    /**
     */
    private void initServer() {
        server = new JamnServer(appConfig);

        // define a provider dispatcher to route requests to the correct content
        // provider
        // using a path prefix check.
        final String[] servicePrefixes = { appConfig.getWebServiceUrlRoot(), "/vres" };
        server.getRequestProcessor().setProviderDispatcher(request -> {
            String path = request.getDecodedPath();
            if (path != null && path.startsWith("/")) {

                for (int i = 0; i < servicePrefixes.length; i++) {
                    if (path.startsWith(servicePrefixes[i])) {
                        return ContentProvider.WEB_SERVICE;
                    }
                }
            }
            return ContentProvider.WEB_CONTENT;
        });

        LOG.info("JamnServer installed");
    }

    /**
     */
    private void initContentProvider() throws IOException, URISyntaxException {

        // create a file cache for the web content provider
        ResourceFileCache<WebFile> resourceCache = new ResourceFileCache<WebFile>(WebContentProvider::newWebFile,
                appConfig);

        // create provider
        webContentProvider = new WebContentProvider(resourceCache);

        // define a path mapper to map the main page paths to the configured home page
        final String homePage = appConfig.getWebAppMainPage();
        webContentProvider.setPathMapper((path, request) -> {
            if ("/".equals(path) || "/index.html".equals(path)) {
                return homePage;
            }
            return path;
        });

        // add the provider to server
        server.addContentProvider(ContentProvider.WEB_CONTENT, webContentProvider);
        LOG.info("Web Content provider [{}] installed at [{}]", webContentProvider.getClass().getSimpleName(),
                appConfig.getWebRoot());
    }

    /**
     */
    private void initWebServiceProvider() {
        webServiceProvider = new WebServiceProvider()
                .setJsonTool(jsonTool)
                .setPlaceholderResolver(text -> Tool.resolvePlaceholder(text, appConfig.getProperties()))
                .setWebResourceRegistry(webContentProvider);

        server.addContentProvider(ContentProvider.WEB_SERVICE, webServiceProvider);

        LOG.info("Web Service provider [{}] installed at [{}]", webServiceProvider.getClass().getSimpleName(),
                appConfig.getWebServiceUrlRoot());
    }

    /**
    */
    private void initWebSocketProvider() {
        webSocketProvider = new WebSocketProvider()
                .addConnectionPath(appConfig.getWebSocketUrlRoot())
                .setMaxUpStreamPayloadSize(appConfig.getWebSocketMaxUpstreamSize());

        wsoMessageDispatcher = new WsoMessageDispatcher(jsonTool, appConfig);
        webSocketProvider.addMessageProcessor(wsoMessageDispatcher);

        server.addContentProvider(ContentProvider.WEB_SOCKET, webSocketProvider);

        LOG.info("WebSocket provider installed [{}] at [{}]", webSocketProvider.getClass().getSimpleName(),
                "ws://<host>:" + appConfig.getHttpServerPort() + appConfig.getWebSocketUrlRoot());
    }

    /**
     */
    private void initJavaScriptProvider() throws IOException {
        if (appConfig.isJavaScriptEnabled()) {
            Tool.ensureSubDir(appConfig.getWorkspaceRoot(), appHome);
            Path scriptPath = Tool.ensureSubDir(appConfig.getScriptRoot(), appHome);

            JavaScriptProvider jsProvider = new JavaScriptProvider(scriptPath, appConfig.getProperties());
            jsProvider.setHostAppAdapter(
                    new JavaScriptAppAdapter(jsProvider, osIFace, appConfig, appHome));

            jsProvider.initialize();
            javaScript = Optional.of(jsProvider);
            LOG.info("JavaScript provider installed with script root [{}]", scriptPath);
        } else {
            LOG.info("Server side JavaScript disabled");
        }
    }

    /**
     */
    private void initExtensionHandler() {
        try {
            Path rootPath = Tool.ensureSubDir(appConfig.getExtensionRoot(), appHome);
            Tool.ensureSubDir(appConfig.getExtensionBin(), rootPath);
            Tool.ensureSubDir(appConfig.getExtensionData(), rootPath);
            Tool.ensureSubDir(appConfig.getWorkspaceRoot(), appHome);

            Path filePath = Paths.get(rootPath.toString(),
                    "ExtensionDefFile.json.template");
            if (!Files.exists(filePath)) {
                String template = jsonTool.toPrettyString(new ExtensionHandler.ExtensionDef());
                Files.writeString(filePath, template, StandardOpenOption.CREATE);
            }

            extensionHandler = new ExtensionHandler(rootPath, this);

            LOG.info("Extension handler installed at root: [{}]", rootPath);
        } catch (Exception e) {
            throw new UncheckedAppException("Error initializing extension handler", e);
        }
    }

    /**
     */
    private void initAppServicesAndObjects() throws WebServiceDefinitionException, IOException {

        // connect the extension handler to web service provider
        // and load all available extensions
        extensionHandler.setWebServiceRegistry(this.webServiceProvider);
        extensionHandler.loadAllExtensions();

        // add task processors to the web socket system
        // these processors will handle the incoming web socket messages
        wsoMessageDispatcher
                .addTaskProcessor(new RunJavaScriptTaskProcessor(javaScript))
                .addTaskProcessor(new RunExtensionTaskProcessor(extensionHandler));

        // register app web services
        webServiceProvider.registerServices(() -> WebAppConfigService.getInstance(this));

        LOG.info("App Services and objects installed");
    }

    /**
     */
    public String getAppName() {
        return appName;
    }

    /**
     */
    public AppConfig getAppConfig() {
        return appConfig;
    }

    /**
     */
    public Charset getStandardEncoding() {
        return standardEncoding;
    }

    /**
     */
    public Path getHomePath(String... subPathParts) {
        return Paths.get(appHome.toString(), subPathParts);
    }

    /**
     */
    public JsonTool getJsonTool() {
        return jsonTool;
    }

    /**
     */
    public WebContentProvider getWebContentProvider() {
        return webContentProvider;
    }

    /**
     */
    public WebServiceProvider getWebServiceProvider() {
        return webServiceProvider;
    }

    /**
     */
    public WebSocketProvider getWebSocketProvider() {
        return webSocketProvider;
    }

    /**
     */
    public Optional<JavaScriptProvider> getJavaScriptProvider() {
        return javaScript;
    }

    /**
     */
    public ExtensionHandler getExtensionHandler() {
        return extensionHandler;
    }
}