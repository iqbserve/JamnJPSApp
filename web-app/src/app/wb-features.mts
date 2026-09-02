/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { WorkView } from 'core/view-classes.mjs';
import { LazyFunction, typeUtil } from 'core/tools.mjs';
import { WorkbenchViewManager } from 'core/view-manager.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';

/* Types */
import type { DynamicFunction } from 'types/commons';
import type { ExtensionSBarItemDef } from 'types/commons';
import { SBarItemDef, WbAppConfig } from 'config/wbapp-config.mjs';

/**
 * The module holds all features of the app
 * as internal Dynamic Functions registered via addFeature()
 */

// feature registry object
const extensionFeatures: Record<string, DynamicFunction> = {};

// extension sidebar item definitions
// to add extensions to the sidebar
const extensionSidebarItems: ExtensionSBarItemDef[] = [];

/**
 */
export function initExtensionFeatures(config: WbAppConfig, cb: () => void) {
    let moduleName = "/app/" + WbProperties.extensionFeaturesModule();
    import(moduleName).then((module) => {
        applyExtensionFeatures(config);
        if(module && module.doCustomization) {
            module.doCustomization(config);
        }
        cb();
    });
}

/**
 * Feature registration/definition function
 */
export function addFeature(name: string, feature: DynamicFunction, sideBarItemDef?: ExtensionSBarItemDef) {
    if (extensionFeatures[name]) {
        throw new Error(`Feature [${name}] already exists`);
    } else {
        extensionFeatures[name] = feature;
    }
    if (sideBarItemDef) {
        sideBarItemDef.feature = name;
        extensionSidebarItems.push(sideBarItemDef);
    }
}

/**
 * Features are invoked/called by using the callFeature() function
 */
export function callFeature(name: string, viewManager: WorkbenchViewManager) {
    if (extensionFeatures[name]) {
        const feature = extensionFeatures[name];
        feature.invoke((retObj: WorkView | (() => void) | null) => {
            if (retObj instanceof WorkView) {
                viewManager.openView(retObj);
            } else if (typeUtil.isFunction(retObj)) {
                retObj();
            } else {
                Logger.warn(`Call to feature [${name}] returned unexpected value [${retObj}]`)
            }
        });
    } else {
        Logger.warn(`Call to unknown feature [${name}]`)
    }
}

/**
 * Applies the extension features to the app configuration.
 */
function applyExtensionFeatures(appConfig: WbAppConfig) {
    extensionSidebarItems.forEach((extItemDef) => {
        const topic = appConfig.getTopicList().find((topic) => topic.text === extItemDef.topic);
        //cast to config type
        const itemDef = { text: extItemDef.item, feature: extItemDef.feature } as SBarItemDef;
        if (topic) {
            topic.items.push(itemDef);
        } else {
            //create a new topic entry if it does not exist
            let sbarTopic = {
                text: extItemDef.topic,
                icon: extItemDef.icon || "",
                items: [itemDef]
            };
            appConfig.getTopicList().push(sbarTopic);
        }
    });
}


