/* Authored by iqbserve.de */
package iqb.jps.webapi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.JPSApp;
import iqb.jps.annotation.WebService;
import iqb.jps.core.AppConfig;
import iqb.jps.core.JsonTool;
import static iqb.jps.JamnServer.HttpHeader.FieldValue.APPLICATION_JSON;

/**
 * <pre>
 * REST like web-app configuration provider service. (explicitly coded)
 * 
 * Example URL:
 * http://localhost:9090/webapi/service/get-wbapp-configuration?name=demo
 * </pre>
 */
public class WebAppConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(WebAppConfigService.class);

    private final JPSApp jpsApp;
    private final JsonTool json;
    private final AppConfig config;
    private final Charset encoding;

    private static WebAppConfigService instance;
    public static synchronized WebAppConfigService getInstance(JPSApp jpsApp) {
        if (instance == null) {
            instance = new WebAppConfigService(jpsApp);
        }
        return instance;
    }

    public WebAppConfigService(JPSApp jpsApp) {
        this.jpsApp = jpsApp;
        this.json = jpsApp.getJsonTool();
        this.config = jpsApp.getAppConfig();
        this.encoding = jpsApp.getStandardEncoding();
    }

    /**
     */
    @WebService(path = "${jps.webservice.url.root}/service/get-wbapp-configuration", methods = { "GET" }, contentType = APPLICATION_JSON)
    public String getWbAppConfiguration(Map<String, String> requestParams)
            throws IOException {
        String name = requestParams.getOrDefault("name", "demo");
        return getConfigContent(name);
    }

    /**
     */
    @SuppressWarnings("unchecked")
    protected String getConfigContent(String configName) throws IOException {
        byte[] bytes;
        String content;
        Map<Object, Object> configData;

        if ("system".equalsIgnoreCase(configName)) {
            configData = new HashMap<>();
            configData.put("sysProps", config.getProperties());
            configData.put("buildProps", config.getBuildProperties());
            content = json.toPrettyString(configData);
        } else {
            String requestPath = "/config/wbapp-config-" + configName + ".json";
            bytes = jpsApp.getWebContentProvider().getWebFileData(requestPath);

            content = new String(bytes, encoding);
            configData = (Map<Object, Object>) json.toObject(content, Object.class);
            enrichAppConfigWith("systemInfo", config.getBuildProperties(), configData);

            String[][] keys = new String[][]{
                {"jps.extension.feature.interface.template", "extensionFeaturesModule"}
            };

            Properties props = new Properties();
            for (String[] keyPair : keys) {
                String propKey = keyPair[0];
                String configKey = keyPair[1];
                if (config.getProperties().containsKey(propKey)) {
                    props.put(configKey, config.getProperties().get(propKey));
                }
            }
            
            enrichAppConfigWith("properties", props, configData);
            
            content = json.toPrettyString(configData);
            LOG.info("Web app configuration read from [{}]:", requestPath);
        }
        return content;
    }

    /**
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void enrichAppConfigWith(String dataName, Properties props, Map config) {
        if (config.containsKey(dataName)) {
            ((Map) config.get(dataName)).putAll(props);
        } else {
            config.put(dataName, props);
        }
    }

}
