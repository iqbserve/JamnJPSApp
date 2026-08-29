# JPSApp Extension Parent pom

This module provides a Maven parent for standalone extension projects that target the JamnJPSApp runtime.

Goal: build extension JARs without cloning or building the JamnJPSApp itself.

## Java Extensions usage

Extensions can realize any kind of user functionality. They can get called as "commands" with a string argument array. Or as WebServices with string serializable in- and output objects (see: JamnJPSApp /sample project). 

## Intended pom usage

Use this parent pom in an external extension project:

```xml
<parent>
  <groupId>iqb.jps</groupId>
  <artifactId>jpsapp-extension-parent</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</parent>
```

If intendet or needed add a dependency to the JPSApp extension api (version managed by the parent):

```xml
<dependencies>
  <dependency>
    <groupId>iqb.jps</groupId>
    <artifactId>${jps.extension.api.artifactId}</artifactId>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

## Key Properties

- `jps.target.app.version`: target JamnJPSApp version for extension compatibility
- `jps.extension.api.artifactId`: core API artifact exposed to extension compile classpath (default: `jpsapp-core`)
- `jps.extensions.bin`: destination path used by optional copy/deploy tasks

External projects can override these properties in their own `pom.xml`.

## External Extension pom Skeleton

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>iqb.jps</groupId>
    <artifactId>jpsapp-extension-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
  </parent>

  <groupId>my.extensions</groupId>
  <artifactId>my-jps-extension</artifactId>
  <version>1.0.0</version>

  <dependencies>
    <dependency>
      <groupId>iqb.jps</groupId>
      <artifactId>${jps.extension.api.artifactId}</artifactId>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
```

## Runtime Deployment Model

Once an extension is build copy the extension jar to the JPSApp root dir: /extensions/bin

Create a minimum extension json def file "extension-name.json" in root dir: /extensions

The name part (extension-name) of this file will get the unique id name of the extension if NO name="my-name" is defined in the def file itself.

  ```json
  {
      "binPath" : "./bin/extension-jar-file-name.jar",
      "className" : "extension-java-class-name",
      "runMethod" : "run"
      ... 
  }
```


By default the App will load all extensions defined in the extensions root folder at startup. That is why extensions must im minimum provide a public constructor. The JPSApp extension api does NOT require a specific lifecycle from extensions. The only entry point is the instantiaton. The def file just provides a singleton=true/false property. 

## WebApp customization interface

If the extension also provides a WebApp feature one more step is necessary to tell the WebApp about the new feature and to plug it into the WebApp side bar.

This has to be done by using javascript and a local user defined web root folder. To enable this intercepting define the JPSApp property e.g. like

- jps.user.web.local.root = user-http

where "user-http" folder is expected to be in the start/root directory of the JPSApp. Absolut pathes starting with a slash 

- /my-project/my-http-files/my-http

are also supported. From this root on you have to use the pathes that are used in the WebApp to overwrite WebApp files. In the case of the default Worbench WebApp create a file

- /app/wb-extension-features.mjs

which is the the interface to define new WebApp features and their insertion into the sidebar.

Example:

```javascript
import { Logger } from 'core/logging.mjs';
import { LazyFunction } from 'core/tools.mjs';

/**
 * <pre>
 * The module is the customizing interface for the workbench web application.
 * - WbExtensionFeatures : defines and creates new feature objects 
 * - WbExtensionSidebarItems : defines new sidebar items by adding config data
 * </pre>
 */

// provide feature definitions
export const WbExtensionFeatures = {
    // create real web app objects
    toolsDBConnections: new LazyFunction('features/db-connections.mjs', "getView")
};

// provide corresponding sidebar item definitions
// as they are used in the JSON config files
export const WbExtensionSidebarItems =  [
    {topic: "Tools", items: [
        { text: "My DB Connections Feature", feature: "toolsDBConnections" }
    ]}
];

Logger.info(`Workbench extension features installed`);

```




