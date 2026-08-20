/* Authored by iqbserve.de */

/**
 * Tools povides some helper functions
 * in particular functions provided by the HostApp
 */

export const version = "0.0.1";

//actual system line separator
export const LS = HostApp.ls()

/**
 * call command in os shell
 * e.g. a os command or a shell script
 */
export function sh(command, workDir = "", outputConsumer = null) {
	let result = HostApp.shellCmd(command, workDir, outputConsumer);
	//convert java type List to javaScript type array
	return Java.from(result);
};

/**
 */
export function isOnUnix() {
	return HostApp.isOnUnix();
};

/**
 * echo/print/log text to java host app
 */
export function echo(text) {
	return HostApp.echo(text);
};

/**
 * get a path string as os dependend path
 */
export function path(...path) {
	return HostApp.path(path);
};

/**
 * get workspace path string 
 */
export function workspacePath(...path) {
	return HostApp.workspacePath(path);
};

/**
 * get app home path string 
 */
export function homePath(...path) {
	return HostApp.homePath(path);
};

/**
 * an object to iterate over an argument list and call corresponding functions 
 */
export class ArgumentProcessor {
	unprocessedArgs = [];

	constructor(unprocessedArgs = []) {
		this.unprocessedArgs = unprocessedArgs;
	}

	parse = (arg) => {
		let parts = arg.split("=");
		if (parts.length == 1) {
			return { key: parts[0], value: parts[0] };
		} else if (parts.length == 2) {
			return { key: parts[0], value: parts[1] };
		}
		return { key: "", value: "" };
	};

	process(argList, cbs) {
		let ret = null;
		if (argList) {

			for (const arg of argList) {
				let argObj = this.parse(arg);
				if (cbs[argObj.key]) {
					ret = cbs[argObj.key](argObj.value);
				} else if (cbs["default"]) {
					ret = cbs["default"](argObj);
				} else {
					this.unprocessedArgs.push(arg);
				}
				if (ret) {
					return ret;
				}
			}
		}
		return ret;
	}
}