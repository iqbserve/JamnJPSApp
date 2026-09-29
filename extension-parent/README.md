# JPSApp Extension Parent pom

This module provides a Maven parent for standalone extension projects that target the JamnJPSApp runtime.

The Goal is - building extensions (JARs) without cloning or building the JamnJPSApp project itself.

For development purpose there is a rudimentary public maven artifact repository at: <a href="https://devrepo.iqbserve.de/">IQBServe development artifact Repository</a>

```xml
<repositories>
  <repository>
    <id>iqbserve-public-devrepo</id>
    <url>https://devrepo.iqbserve.de</url>
  </repository>
</repositories>
```

## Intended pom usage

Use this parent pom in a external extension project:

```xml
<!-- Default parent POM for JPSApp extensions -->
<parent>
  <groupId>iqb.jps</groupId>
  <artifactId>jpsapp-extension-parent</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</parent>
```

If needed add a dependency to the JPSApp extension api:

```xml
<dependency>
  <groupId>iqb.jps</groupId>
  <artifactId>${jps.extension.api.artifactId}</artifactId>
  <scope>provided</scope>
</dependency>
```

## Key Properties

```xml
  <!-- the version reflects the JPSApp version -->
  <jps.extension.api.version>${revision}</jps.extension.api.version>

  <!-- the extension api is part of the core lib -->
  <jps.extension.api.artifactId>jpsapp-core</jps.extension.api.artifactId>

  <!-- this path reflects the runtime situation -->
  <jps.extensions.bin>./extensions/bin</jps.extensions.bin>

```

## Runtime Deployment Model

The "root" dir "./" is the directory where the JPSApp is started.

Copy the extension jar to: ./extensions/bin

Create a minimum extension json def file "extension-name.json" in: ./extensions

The name part ("extension-name".json) of the json definition file is the default unique id name for the extension - if NO name="my-name" is defined in the definition file itself.

Minimal java extension definition file example:

```json
{
  "binPath" : "./bin/extension-jar-file-name.jar",
  "className" : "extension-java-class-name",
    ... 
}
```

By default JPSApp will load all extensions defined in the extensions root folder at startup. That is why java extensions must im minimum provide a public constructor. The JPSApp extension api does NOT require a specific lifecycle from extensions. The entry point is the instantiaton. The def file just provides a singleton=true/false property. 

## WebApp customization interface

If the extension also is or provides a WebApp feature it has to get pluged into the WebApp.

The current mechanism for that is javascript based and requires javascript and json definitions as web resources. The basic entrypoint for this is the wb-extension-features.mjs file in the extensions root folder (NOT in the web-app project itself). The file is plugged into to the Web App and adds or configures objects and configurations.

There are several ways to define and add such js code - but in the end they are all written to the wb-extension-features.mjs file. The file has a public area where code can be directly placed - and a placeholder which is the anchor for added and generated code. Normally, it is not necessary to edit this file — all necessary definitions can be created directly within or alongside the respective extensions and will then automatically end up in this file at runtime.

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





