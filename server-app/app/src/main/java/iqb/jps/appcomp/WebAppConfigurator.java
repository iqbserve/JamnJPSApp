/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import iqb.jps.core.HelperTool;
import iqb.jps.core.WebResourceRegistry;
import iqb.jps.extapi.ExtensionWebAppConfigurator;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * <pre>
 * The class realizes an interface api to add features, configurations, and
 * resources to
 * the web application from java.
 * The contract is:
 * - the web app provides an resource based interface (e.g. js modules)
 * to add and configure new features
 * - the WebAppConfigurator collects suitable definitions and resources
 * and adds them as the expected interface resources to the resource registry
 * </pre>
 */
public class WebAppConfigurator implements ExtensionWebAppConfigurator {

    private static final HelperTool Tool = HelperTool.getInstance();

    private List<String> features = new ArrayList<>();
    private List<String> sidebarItems = new ArrayList<>();
    private Map<String, byte[]> resources = new HashMap<>();

    private String template = "";
    private String interfaceResourcePath = "";

    private WebResourceRegistry webResourceRegistry;

    protected WebAppConfigurator() {
    }

    public WebAppConfigurator(WebResourceRegistry webResourceRegistry) {
        this.webResourceRegistry = webResourceRegistry;
    }

    /**
     * Specific to this implementation and the jps web app config mechanism
     */
    public WebAppConfigurator setInterfaceResource(String path, String name) throws IOException {
        this.interfaceResourcePath = path + name;
        this.template = Tool.readStringResourceFrom(getClass(), "/" + name + ".txt");
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
    public ExtensionWebAppConfigurator addConfiguration(String configJsonCode) {
        sidebarItems.add(configJsonCode);
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
        StringBuilder featureDefs = new StringBuilder("{");
        features.forEach(item -> featureDefs.append(item).append(",\n"));
        if (featureDefs.length() > 1) {
            featureDefs.setLength(featureDefs.length() - 2); // Remove the trailing comma and newline
        }
        featureDefs.append("}");

        StringBuilder sidebarItemsJson = new StringBuilder("[");
        sidebarItems.forEach(item -> sidebarItemsJson.append(item).append(",\n"));
        if (sidebarItemsJson.length() > 1) {
            sidebarItemsJson.setLength(sidebarItemsJson.length() - 2); // Remove the trailing comma and newline
        }
        sidebarItemsJson.append("]");

        this.resources.put(interfaceResourcePath, template.replace("${featureDefs}", featureDefs.toString())
                .replace("${sidebarItemsList}", sidebarItemsJson.toString()).getBytes());
    }
}
