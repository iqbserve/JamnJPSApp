/* Authored by iqbserve.de */

import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';
import { CommandView } from 'app/features/command.mjs';

import type { ExtenderFunction } from 'types/commons';

/**
 * A Use case specific extension of the general command view.
 */
export const extendView: ExtenderFunction<CommandView> = (view: CommandView) => {
    const originRunMethod = view.runCommand.bind(view);
    //create a new run method - with a confirmation dialog
    view.runCommand = () => {
        WbApp.confirm({
            message: `<b>Start a full project build</b><br>Do you want to start the build process now?`
        }, (val) => {
            if (val) { originRunMethod() }
        });
    };
}