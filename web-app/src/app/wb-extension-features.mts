/* Authored by iqbserve.de */

/* needed Types */
import type { DynamicFunction, ExtensionSidebarItem } from 'types/commons';

/**
 * <pre>
 * The module is the customizing interface for the workbench web application.
 * - WbExtensionFeatures : defines and creates new feature objects 
 * - WbExtensionSidebarItems : defines new sidebar items by adding config data
 * </pre>
 */

export const WbExtensionFeatures: Record<string, DynamicFunction> = {}

export const WbExtensionSidebarItems: ExtensionSidebarItem[] = [];

    