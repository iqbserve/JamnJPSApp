/* Authored by iqbserve.de */
package iqb.jps.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 */
public class AppConfig {
    protected Properties props = new Properties();
    protected Properties buildProps = new Properties();
    protected Path appHome = null;

    /**
     */
    public AppConfig(StringReader propsSource) throws IOException {
        props.load(propsSource);
    }

    /**
     */
    public AppConfig(InputStream propsSource) throws IOException {
        props.load(propsSource);
    }

    /**
     */
    public static String getDefaultConfig(String appName) {
        return String.join("\n",
                "##",
                "## " + appName + " Config Properties",
                "##", "", DEFAULT_CONFIG,
                DEFAULT_SERVER_CONFIG);
    }

    // CONFIG DEFAULT folder names under the home directory
    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private static final String WEB_FILE_ROOT = "http";
    private static final String SCRIPT_ROOT = "scripts";
    private static final String DATA_ROOT = "data";
    private static final String EXTENSION_ROOT = "extensions";
    private static final String EXTENSION_BIN = "bin";
    private static final String EXTENSION_DATA = "data";
    private static final String WORKSPACE_ROOT = "workspace";

    public static final String DEFAULT_SERVER_CONFIG = String.join("\n",
            "##",
            "## " + " Server Config Properties",
            "##", "",
            "#Max concurrent HTTP/WebSocket connections", "http.max.connections=200", "",
            "#Max HTTP header size in bytes", "http.max.header.size=16384", "",
            "#Max HTTP request body size in bytes", "http.max.content.length=5242880", "",
            "#Socket timeout in millis", "http.client.socket.timeout=500", "",
            "#Use Connection:keep-alive header", "http.connection.keep.alive=true", "",
            "#Http encoding", "http.encoding=" + StandardCharsets.UTF_8.name(), "",
            "#A Global Cross origin flag\n#if=true ALL cors requests are allowed", "http.allow.all.cors.enabled=" + FALSE, "",
            "#HTTP CORS",
            "http.cors.allow.origin=localhost",
            "http.cors.allow.methods=GET, POST, OPTIONS",
            "http.cors.allow.headers=Origin, Content-Type, Authorization", ""
             
        );

    private static final String DEFAULT_CONFIG = String.join("\n",
            "#Profile", "jps.profile" + "=app", "",
            "#HTTP Server port", "http.server.port=9090", "",
            "#System Web files root folder\n#intended for the system web app\n#no drive letter, start with '/' = absolute path, else relative to jar or to start folder\n#DO NOT change unless absolutely necessary", "jps.web.root=" + WEB_FILE_ROOT, "",
            "#User Web files local folder\n#intended for web app customization\n#no drive letter, start with '/' = absolute path, else relative to start folder\n#e.g. /my-local-path/user-http, user-http", "#jps.user.web.local.root=user-http", "",
            "#WebApp main Page", "jps.webapp.main.page=/workbench.html", "",
            "#Web content cache mode", "jps.web.cache.load.onstartup=" + FALSE, "",
            "#Web content caching enabled", "jps.web.cache.caching.enabled=" + TRUE, "",
            "#Extensions root folder name", "jps.extension.root=" + EXTENSION_ROOT, "",
            "#Extensions bin folder name", "jps.extension.bin=" + EXTENSION_BIN, "",
            "#Extensions data folder name", "jps.extension.data=" + EXTENSION_DATA, "",
            "#Command task worker", "jps.command.task.worker=10", "",
            "#Workspace root folder", "jps.workspace.root=" + WORKSPACE_ROOT, "",
            "#Data root folder", "jps.data.root=" + DATA_ROOT, "",
            "#JavaScriptProvider script root folder", "jps.script.root=" + SCRIPT_ROOT, "",
            "#JavaScript auto-load script", "jps.js.auto.load.script=js-auto-load.js", "",
            "#JavaScript enabled", "jps.javascript.enabled=" + TRUE, "",
            "#JavaScript debug enabled", "jps.javascript.debug.enabled=" + FALSE, "",
            "#WebSocket url root", "jps.websocket.url.root=/wsoapi", "",
            "#WebSocket max upstream size", "jps.websocket.max.upstream.size=65000", "",
            "#WebSocket Task worker threads", "jps.websocket.task.worker=10", "",
            "#WebService url root", "jps.webservice.url.root=/webapi", "",
            "#Standard encoding", "jps.standard.encoding=UTF-8", "",
            "#Windows shell encoding", "jps.win.shell.encoding=Cp850", "",
            "#Unix shell encoding", "jps.unix.shell.encoding=ISO8859_1", "",
            "#JVM debug option",
            "jvm.debug.option=-agentlib:jdwp=transport=dt_socket,address=localhost:9009,server=y,suspend=y", "");

    public String getAppName() {
        return props.getProperty("app.name", "");
    }

    public Path getHomePath() {
        if (appHome == null) {
            appHome = Paths.get(props.getProperty("app.home", System.getProperty("user.dir")));
        }
        return appHome;
    }

    public Properties getProperties() {
        return props;
    }

    public Properties getBuildProperties() {
        return buildProps;
    }

    public void searchDynamicOption(String key, String defaultValue) {
        // overwriting: if -D present -> use it or leave current
        props.put(key, System.getProperty(key, props.getProperty(key, defaultValue)));
    }

    public int getHttpServerPort() {
        return Integer.valueOf(props.getProperty("http.server.port", "9090"));
    }

    /**
    */
    public void setHttpServerPort(int port) {
        props.setProperty("http.server.port", String.valueOf(port));
    }

    /**
    */
    public void setActualHttpServerPort(int port) {
        props.setProperty("actual.http.server.port", String.valueOf(port));
    }

    public String getWebRoot() {
        return props.getProperty("jps.web.root", WEB_FILE_ROOT);
    }

    public String getUserWebLocalRoot() {
        return props.getProperty("jps.user.web.local.root", "");
    }

    public String getDataRoot() {
        return props.getProperty("jps.data.root", DATA_ROOT);
    }

    public String getExtensionRoot() {
        return props.getProperty("jps.extension.root", EXTENSION_ROOT);
    }

    public String getExtensionBin() {
        return props.getProperty("jps.extension.bin", EXTENSION_BIN);
    }

    public String getExtensionData() {
        return props.getProperty("jps.extension.data", EXTENSION_DATA);
    }

    public String getWorkspaceRoot() {
        return props.getProperty("jps.workspace.root", WORKSPACE_ROOT);
    }

    public String getWebAppMainPage() {
        return props.getProperty("jps.webapp.main.page", "/workbench.html");
    }

    public boolean webCacheLoadOnStartup() {
        return Boolean.parseBoolean(props.getProperty("jps.web.cache.load.onstartup", FALSE));
    }

    public boolean webCacheCachingEnabled() {
        return Boolean.parseBoolean(props.getProperty("jps.web.cache.caching.enabled", TRUE));
    }

    public String getScriptRoot() {
        return props.getProperty("jps.script.root", SCRIPT_ROOT);
    }

    public String getJsAutoLoadScript() {
        return props.getProperty("jps.js.auto.load.script", "js-auto-load.js");
    }

    public String getStandardEncoding() {
        return props.getProperty("jps.standard.encoding", "UTF-8");
    }

    public String getWinShellEncoding() {
        return props.getProperty("jps.win.shell.encoding", "Cp850");
    }

    public String getUnixShellEncoding() {
        return props.getProperty("jps.unix.shell.encoding", "ISO8859_1");
    }

    public String getWebSocketUrlRoot() {
        return props.getProperty("jps.websocket.url.root", "/wsoapi");
    }

    public long getWebSocketMaxUpstreamSize() {
        return Long.valueOf(props.getProperty("jps.websocket.max.upstream.size", "65000"));
    }

    public int getWebSocketTaskWorker() {
        return Integer.valueOf(props.getProperty("jps.websocket.task.worker", "10"));
    }

    public int getJPSCommandTaskWorker() {
        return Integer.valueOf(props.getProperty("jps.command.task.worker", "10"));
    }

    public String getWebServiceUrlRoot() {
        return props.getProperty("jps.webservice.url.root", "/webapi");
    }

    public boolean isJavaScriptEnabled() {
        return Boolean.parseBoolean(props.getProperty("jps.javascript.enabled", FALSE));
    }

    public boolean isJavaScriptDebugEnabled() {
        return Boolean.parseBoolean(props.getProperty("jps.javascript.debug.enabled", FALSE));
    }

    public boolean hasAppProfile() {
        return props.getProperty("jps.profile", "app").equals("app");
    }

    protected Set<String> httpCorsAllowOrigin = null;

    public Set<String> httpCorsAllowOrigin() {
        if (httpCorsAllowOrigin == null) {
            String[] values = Arrays.stream(
                    props.getProperty("http.cors.allow.origin").trim()
                            .split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
            httpCorsAllowOrigin = new HashSet<>(Arrays.asList(values));
        }
        return httpCorsAllowOrigin;
    }

    public String httpCorsAllowMethods() {
        return props.getProperty("http.cors.allow.methods", "");
    }

    public String httpCorsAllowHeaders() {
        return props.getProperty("http.cors.allow.headers", "");
    }

    /**
    */
    public int getHttpMaxConnections() {
        return Integer.valueOf(props.getProperty("http.max.connections", "200"));
    }

    /**
    */
    public int getHttpMaxHeaderSize() {
        return Integer.valueOf(props.getProperty("http.max.header.size", "16384"));
    }

    /**
    */
    public int getHttpMaxContentLength() {
        return Integer.valueOf(props.getProperty("http.max.content.length", "5242880"));
    }

    /**
    */
    public String getHttpEncoding() {
        return props.getProperty("http.encoding", "UTF-8");
    }

    /**
    */
    public boolean isHttpConnectionKeepAlive() {
        return Boolean.parseBoolean(props.getProperty("http.connection.keep.alive", FALSE));
    }

    /**
     */
    public int getHttpClientSocketTimeout() {
        return Integer.valueOf(props.getProperty("http.client.socket.timeout", "10000"));
    }

    /**
     */
    public boolean isHttpAllowAllCORSEnabled() {
        return Boolean.parseBoolean(props.getProperty("http.allow.all.cors.enabled", FALSE));
    }

    /**
    */
    public void setAllowAllCORSEnabled(boolean val) {
        props.setProperty("http.allow.all.cors.enabled", String.valueOf(val));
    }

    /**
     */
    @Override
    public String toString() {
        return props.toString();
    }

}