/* Authored by iqbserve.de */

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
    // feature paths must take in concern if a importmap is used
    toolsDBConnections: new LazyFunction('features/db-connections.mjs', "getView")
};

// provide corresponding sidebar item definitions
// as they are used in the JSON config files
export const WbExtensionSidebarItems =  [
    {topic: "Tools", items: [
        { text: "My DB Connections", feature: "toolsDBConnections" }
    ]}
];

Logger.info(`Workbench extension features installed`);