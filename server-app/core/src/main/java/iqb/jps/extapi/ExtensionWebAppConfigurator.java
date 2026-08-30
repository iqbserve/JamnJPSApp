/* Authored by iqbserve.de */
package iqb.jps.extapi;

public interface ExtensionWebAppConfigurator {

    /**
     */
    public ExtensionWebAppConfigurator addFeature(String featureCode);

    /**
     */
    public ExtensionWebAppConfigurator addConfiguration(String configJsonCode);

    /**
     */
    public ExtensionWebAppConfigurator addResource(String resourcePath, byte[] resourceContent);

    /**
     */
    public void build();

}
