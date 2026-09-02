## WebApp customization interface (sample)

The WebApp customization root folder as defined in the jps.properties e.g.

- jps.user.web.local.root = user-http

Any WebApp resource file (mjs, html, css, etc.) identified by <path/name.ext> that is found here - overwrites the original file from the Web Application bundled with the JPSApp.

The only exceptions are files that are directly created oder loaded by the system into the resource file cache.

This applies especially to the <b>extensions feature interface</b> file which is NOT reachable over the user-http folder. This file <b>resides in the extensions folder.</b>