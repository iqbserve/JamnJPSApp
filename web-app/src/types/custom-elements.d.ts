/* Authored by iqbserve.de */

/**
 * Registers the app's custom elements (see uicomponents.mts / registerUIWebComponents)
 * on HTMLElementTagNameMap so document.createElement/querySelector infer the concrete class.
 */
import type { WbTitlebar, WbStatusline, WbSidebar, ActionIcon, SimpleCrudComp } from 'core/uicomponents.mjs';

declare global {
    interface HTMLElementTagNameMap {
        "wb-titlebar": WbTitlebar;
        "wb-statusline": WbStatusline;
        "wb-sidebar": WbSidebar;
        "a-icon": ActionIcon;
        "simple-crud-comp": SimpleCrudComp;
    }
}
