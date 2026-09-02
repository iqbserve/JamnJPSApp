/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.core.AppConfig;
import iqb.jps.core.ExprString;
import iqb.jps.core.HelperTool;
import iqb.jps.core.WebResourceRegistry;
import iqb.jps.extapi.ExtensionWebAppConfigurator;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * <pre>
 * The class realizes an interface to add features and resources to
 * the web application from java.
 * </pre>
 */
public class WebAppConfigurator implements ExtensionWebAppConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(WebAppConfigurator.class);

    private static final HelperTool Tool = HelperTool.getInstance();

    private List<String> features = new ArrayList<>();
    private Map<String, byte[]> resources = new HashMap<>();

    private String template = "";
    private String interfaceResourcePath = "";

    private WebResourceRegistry webResourceRegistry;
    private AppConfig appConfig;

    protected WebAppConfigurator() {
    }

    public WebAppConfigurator(WebResourceRegistry webResourceRegistry, AppConfig appConfig) {
        this.webResourceRegistry = webResourceRegistry;
        this.appConfig = appConfig;
    }

    /**
     * Specific to this implementation and the jps web app config mechanism
     */
    public WebAppConfigurator setInterfaceResource(String path, String fileName, Path extensionRoot) throws IOException {

        this.interfaceResourcePath = path + fileName;
        Path templatePath = Paths.get(extensionRoot.toString(), fileName);

        if (Files.exists(templatePath)) {
            this.template = new String(Files.readAllBytes(templatePath), appConfig.getStandardEncoding()).trim();
            LOG.info("Use local file as web app interface [{}]", templatePath);
        } else {
            this.template = Tool.readStringResourceFrom(getClass(), "/" + fileName + ".txt");
            LOG.info("Use default resource as web app interface [{}]", fileName);
        }
        return this;
    }

    /**
     * Builds the interface resource and runs the resource registration
     */
    @Override
    public void build() {
        createInterfaceResource();
        resources.forEach((path, content) -> webResourceRegistry.registerResource(path, content));
    }

    @Override
    public ExtensionWebAppConfigurator addFeature(String featureCode) {
        features.add(featureCode);
        return this;
    }

    @Override
    public ExtensionWebAppConfigurator addResource(String resourcePath, byte[] resourceContent) {
        resources.put(resourcePath, resourceContent);
        return this;
    }

    /**
     * Creates the expected feature and config resources.
     */
    private void createInterfaceResource() {
        StringBuilder featureDefs = new StringBuilder();
        features.forEach(item -> featureDefs.append(item).append("\n"));

        String resource = ExprString.applyValues(template, (key, ctx) -> 
             key.equals("featureDefs") ? featureDefs.toString() : null
        );
        this.resources.put(interfaceResourcePath, resource.getBytes());
    }
}
