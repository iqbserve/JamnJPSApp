/* Authored by iqbserve.de */

import { WbProperties } from 'config/wbapp-properties.mjs';

export const ALL: number = 0;

/**
 * Trivial entry point for logging
 */
class LoggingProvider {
	level = ALL;
	constructor(properties: { [key: string]: string | number | boolean }) {
		this.level = (properties?.logLevel || ALL) as number;
	}
	consoleLog(msg: unknown) {
		console.log(msg);
	}
	info(msg: unknown) {
		console.log(msg);
	}
	warn(msg: unknown) {
		console.warn(msg);
	}
	debug(msg: unknown) {
		console.debug(msg);
	}
	error(msg: unknown) {
		console.error(msg);
	}
}

export const Logger = new LoggingProvider(WbProperties.getLoggingProperties());
