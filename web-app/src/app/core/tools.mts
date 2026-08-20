/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';
import { DataFile } from './data-classes.mjs';

/* Types */
import type { DynamicFunction, FncArgs } from 'types/commons';

/**
 * Some helper functions and constants
 */

export const NL = "\n";

/**
 * Object to define functions
 * that get lazily resolved at the first call
 */
export class LazyFunction implements DynamicFunction {
	#moduleName = "";
	#functionName = "";
	#functionArgs = null;
	#returnOnly = false;

	constructor(module: string, fncName: string, fncArgs: FncArgs = null) {
		this.#moduleName = module;
		this.#functionName = fncName;
		this.#functionArgs = fncArgs;
	}

	/**
	 * Set to return function mode.
	 * Do not call but return the function on invocation.
	 */
	setReturnFunctionMode(): LazyFunction {
		this.#returnOnly = true;
		return this;
	}

	invoke(cb: (retVal: unknown) => void): void {
		import(this.#moduleName)
			.then((module) => {
				let retVal: unknown;
				if (this.#returnOnly) {
					//return the function
					retVal = module[this.#functionName]
				} else if (this.#functionArgs) {
					//call the function with arguments
					retVal = module[this.#functionName](this.#functionArgs);
				} else {
					//call the function without arguments
					retVal = module[this.#functionName]();
				}
				cb(retVal);
			});
	}
}

/**
 */
export function checkUrlAvailable(url: string, cb: (available: boolean) => void, timeout = 2000): void {
	const controller = new AbortController();
	const id = setTimeout(() => controller.abort(), timeout);
	Logger.debug(`Looking for ... ${url}, ${id}`);

	fetch(url, {
		method: 'HEAD',
		mode: 'no-cors',
		signal: controller.signal
	}).then(() => {
		clearTimeout(id);
		Logger.debug(`isUrlAvailable: TRUE: ${id}`);
		cb(true);
	}).catch((e) => {
		clearTimeout(id);
		Logger.debug(`isUrlAvailable FALSE: ${id} - ${e.message}`);
		cb(false);
	});
}

/**
 */
export function BackendServerUrl(...path: string[]): string {
	let url = WbProperties.webBackendServerUrl(window.location.origin);

	const urlPath = path.join("/");
	if (urlPath.startsWith("/")) {
		url = url + urlPath;
	} else {
		url = url + "/" + urlPath;
	}
	return url;
}

/**
 */
export function OriginServerUrl(...path: string[]): string {
	let url = window.location.origin;

	const urlPath = path.join("/");
	if (urlPath.startsWith("/")) {
		url = url + urlPath;
	} else {
		url = url + "/" + urlPath;
	}
	return url;
}

/**
 */
export function decodeRequestParameter(href: string): Map<string, string> {
	const paramMap = new Map(new URL(href).searchParams.entries());
	return paramMap;
}

export function styleFloat(elem: Element, prop: string): number {
	return Number.parseFloat(window.getComputedStyle(elem)[prop]) || 0;
}

/**
 */
export function findChildOf(root: Element, childId: string): Element | null {
	const selector = `#${CSS.escape(childId)}`;
	return root.querySelector(selector);
}

/**
 */
export function setVisibility(elem: HTMLElement, flag: boolean): HTMLElement {
	elem.style["visibility"] = flag ? "visible" : "hidden";
	return elem;
}

/**
 */
export function setDisplay(elem: HTMLElement, flag: boolean | string): HTMLElement {
	if (typeof flag == "boolean") {
		elem.style["display"] = flag ? "block" : "none";
	} else if (typeof flag == "string") {
		elem.style["display"] = flag;
	}
	return elem;
}

/**
 */
export function newSimpleId(prfx = ""): string {
	return prfx + Math.random().toString(16).slice(2);
}

/**
 */
export function mergeArrayInto<T>(target: T[], source: T[], allowDuplicates = false): T[] {
	target = target || [];
	source = source || [];
	//copy target
	target = [...target];
	source.forEach(value => {
		if (!target.includes(value) || allowDuplicates) {
			target.push(value);
		}
	});
	return target;
}

/**
 */
export const fileUtil = {

	/**
	 */
	saveToFileFapi: (fileName: string, text: string) => {
		window["showSaveFilePicker"]({
			suggestedName: fileName,
			types: [{
				description: "Text file",
				accept: { "text/plain": [".txt"] },
			}],
		}).then(async handler => {
			const file = await handler.createWritable();
			await file.write(text);
			await file.close();
		}).catch(err => Logger.error(err));
	},

	/**
	 */
	saveToFileClassic: (fileName: string, text: string) => {
		const blob = new Blob([text], { type: "text/plain" });
		const url = URL.createObjectURL(blob);

		const a = document.createElement("a");
		a.href = url;
		a.download = fileName;
		a.style.display = "none";
		a.click();
		URL.revokeObjectURL(url);
	},

	/**
	 */
	createFileInputElement: (fileTypes: string, cb: (evt: Event) => void) => {
		const fileInput = document.createElement("input");
		fileInput.type = "file";
		fileInput.style.display = "none";
		fileInput.accept = fileTypes;
		fileInput.addEventListener("change", cb);
		return fileInput;
	},

	/**
	 * Check if the Modern File API is available
	 */
	isFSApiSupported() {
		return 'showOpenFilePicker' in window;
	}

}

/**
 */
export class FileDataReader {

	#fileTypes: string;
	#dataCb: (dataFile: DataFile) => void;
	#fsapi = false;

	#fileInput: HTMLInputElement;

	constructor(fileTypes: string, dataCb: (dataFile: DataFile) => void, fsapi = false) {
		this.#fileTypes = fileTypes;
		this.#dataCb = dataCb;
		this.#fsapi = fsapi;
		if (!this.#fsapi) {
			this.#fileInput = document.createElement("input");
			this.#fileInput.type = "file";
			this.#fileInput.style.display = "none";
			this.#fileInput.accept = this.#fileTypes;
			this.#fileInput.addEventListener("change", (evt) => {
				const input = evt.target as HTMLInputElement;
				const file = input.files.length > 0 ? input.files[0] : null;
				this.#getFileDataFrom(file);
			});
		}
	}

	#getFileDataFrom(file: File) {
		let dataFile: DataFile = null;
		if (file) {
			dataFile = new DataFile(file.name, new Date(file.lastModified).toLocaleTimeString(), null);
			file.text().then((textData) => {
				dataFile.data = textData;
				this.#fileInput.value = "";
				this.#dataCb(dataFile);
			});
		}
	}

	chooseFile() {
		if (this.#fsapi) {
			//for future use of fsapi
		} else {
			this.#fileInput.click();
		}
	}
}

/**
 */
export const typeUtil = {

	isString: (val: unknown): boolean => {
		return (typeof val === 'string');
	},

	isObject: (val: unknown): boolean => {
		return (val !== null && typeof val === 'object');
	},

	isDomElement: (val: unknown): boolean => {
		return (val !== null && (val instanceof Element));
	},

	isArray: (val: unknown): boolean => {
		return Array.isArray(val);
	},

	isFunction: (val: unknown): boolean => {
		return (val !== null && (typeof val === 'function'));
	},

	isNumber: (val: unknown): boolean => {
		return (val !== null && typeof val === 'number');
	},

	isBoolean: (val: unknown): boolean => {
		return (val === true || val === false);
	},

	isBooleanString: (val: unknown): boolean => {
		return (val === "true" || val === "false");
	},

	booleanFromString: (val: unknown): boolean | null => {
		if (typeUtil.isBooleanString(val)) {
			return (val === "true");
		}
		return null;
	},

	stringFromBoolean: (val: unknown): string | null => {
		if (typeUtil.isBoolean(val)) {
			return val ? "true" : "false";
		}
		return null;
	}

}

/**
 */
export function asDurationString(ms: number) {
	const hours = String(Math.floor(ms / 3600000)).padStart(2, '0');
	const minutes = String(Math.floor((ms % 3600000) / 60000)).padStart(2, '0');
	const seconds = String(Math.floor((ms % 60000) / 1000)).padStart(2, '0');
	const milliseconds = String(ms % 1000).padStart(3, '0');

	return `${hours}:${minutes}:${seconds}:${milliseconds}`;
}


