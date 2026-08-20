/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { WorkView } from 'core/view-classes.mjs';
import { CommandDef } from 'core/data-classes.mjs';
import { LazyFunction, typeUtil } from 'core/tools.mjs';
import { WorkbenchViewManager } from 'core/view-manager.mjs';

/* Types */
import type { DynamicFunction } from 'types/commons';

/**
 * The module provides the user functionalities of the app
 */
const WbFeatures: Record<string, DynamicFunction> = {
    systemLogin: new LazyFunction("app/workbench.mjs", "processSystemLogin").setReturnFunctionMode(),

    systemInfos: new LazyFunction("features/system-infos.mjs", "getView"),

    cmdSampleShellCall: new LazyFunction("features/command.mjs", "getView",
        [
            "cmdSampleShellCallView",
            new CommandDef("Sample: [ js shell command ]", "runjs", "/sample/shell-call.mjs")
                .setOption("args", true)
        ]
    ),
    cmdSampleBuildProject: new LazyFunction("features/command.mjs", "getView",
        [
            "cmdSampleBuildProjectView",
            new CommandDef("Sample: [ js build script ]", "runjs", "/sample/build-project.mjs"),
            new LazyFunction("features/extender/sample-build-project.ext.mjs", "extendView").setReturnFunctionMode()
        ]
    ),
    cmdSampleExtension: new LazyFunction("features/command.mjs", "getView",
        [
            "cmdSampleExtensionView",
            new CommandDef("Sample: [ java extension command ]", "runext", "sample.Command")
                .setOption("args", true)
        ]
    ),

    toolsDBConnections: new LazyFunction('features/db-connections.mjs', "getView"),
    toolsJSPlayground: new LazyFunction('features/js-playground.mjs', "getView")
}

/**
 */
export function callFeature(name: string, viewManager: WorkbenchViewManager) {
    if (WbFeatures[name]) {
        const feature = WbFeatures[name];
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
 */
export function addFeature(name: string, feature: DynamicFunction) {
    if (WbFeatures[name]) {
        throw new Error(`Feature [${name}] already exists`);
    } else {
        WbFeatures[name] = feature;
    }
}
