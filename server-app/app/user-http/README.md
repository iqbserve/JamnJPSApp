## WebApp customization interface (sample)

The WebApp customization root folder as defined in the jps.properties e.g.

- jps.user.web.local.root = user-http

Any WebApp resource file (mjs, html, css, etc.) identified by <path/name.ext> that is found here - overwrites the original file from the Web Application bundled with the JPSApp.

The main customization file is

- /app/wb-extension-features.mjs

The original file in the WebApp is empty. It allows to add new features to the WebApp and to add new items to the sidebar.

The Java WebAppConfigurator uses the same mechanism, but with the difference that it does not write its resources to a file on the hard drive; instead, it passes them directly to the internal resource provider via the WebResourceRegistry.