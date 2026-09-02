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

If the extension also is or provides a WebApp feature it has to get pluged into the WebApp.

The current mechanism for that is javascript based and requires javascript and json definitions as web resources. The basic entrypoint for this is the wb-extension-features.mjs file in the extensions root folder (NOT in the web-app project itself). The file is plugged into to the Web App and adds or configures objects and configurations.

There are different ways to define and add such js code - but in the end they are all written to the wb-extension-features.mjs file. The file has a public area where code can be directly placed - and a placeholder which is the anchor for added and generated code. Normally, it is not necessary to edit this file — all necessary definitions can be created directly within or alongside the respective extensions and will then automatically end up in this file at runtime.

```javascript
import { Logger } from 'core/logging.mjs';
import { LazyFunction } from 'core/tools.mjs';
import { addFeature } from 'app/wb-features.mjs';
import { CommandDef } from 'core/data-classes.mjs';

/**
 * <pre>
 * Extensions WebApp customization interface file.
 * Place customization efforts in doCustomization() function.
 * </pre>
 */
export function doCustomization(config) {
}

// -----------------------------------------------------------------
// Feature definitions placeholder 
// DO NOT change, edit, format or remove this custom placeholder
${ featureDefs }

Logger.info("Web App extension features installed");
```

For example: if a feature definition for an extension is needed - the js code can be placed into a file under extensions/features named like the extension definition file itself with ".feature.js" as suffix instead of ".json".

```javascript
// file content with addFeater snippet
addFeature("cmdSampleExtension", new LazyFunction("features/command.mjs", "getView",
    [
        "cmdSampleExtensionView",
        new CommandDef("Sample: [ java extension command ]", "runext", "sample-command")
            .setOption("args", true)
    ]
), { topic: 'Server Commands', item: 'Sample: Java command' });
```





