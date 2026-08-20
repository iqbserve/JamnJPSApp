/* Authored by iqbserve.de */

/* Types */
import type { JSObject } from "types/commons";

/**
 * Some simple data classes.
 */

/**
 * A common websocket message.
 */
export class WsoCommonMessage {

	//header data
	reference = "";
	command = "";
	functionModule = "";
	argsSrc = "";
	status = "";
	error = "";
	//payload
	bodydata = "";
	attachments = {};

	constructor(reference: string) {
		this.reference = reference;
	}

	hasReference(id: string) {
		return this.reference === id;
	}

	hasStatusSuccess() {
		return "success" === this.status.toLowerCase();
	}

	hasStatusError() {
		return "error" === this.status.toLowerCase();
	}

	setStatusError(errorInfo: string) {
		this.status = "error";
		this.error = errorInfo;
	}

	addAttachment(key: string, value: unknown) {
		this.attachments[key] = value;
	}
}

/**
 * A common command definition.
 */
export class CommandDef {
	title = "";
	command = "";
	script = "";
	options: JSObject = { args: false };

	constructor(title: string, command: string, script: string) {
		this.title = title;
		this.command = command;
		this.script = script;
	}

	setOption(key: string, value: unknown) {
		this.options[key] = value;
		return this;
	}
}

/**
 * View definition struct.
 */
export class ViewSource {
	#file = "";
	#html = null;
	#htmlLoadListener: (viewSrc: ViewSource) => void = () => { };

	constructor(file: string) {
		this.#file = file;
	}

	setHtmlLoadListener(cb: (viewSrc: ViewSource) => void) {
		this.#htmlLoadListener = cb;
	}

	isEmpty() {
		return this.#html == null;
	}

	setHtml(html: string) {
		this.#html = html;
		this.#htmlLoadListener(this);
	}

	getHtml() {
		return this.#html;
	}

	getFile() {
		return this.#file;
	}
}

/**
 */
export class DataFile {
	name = "";
	date = "";
	data: string | null = null;

	constructor(name: string, date: string, data: string | null = null) {
		this.name = name;
		this.date = date;
		this.data = data;
	}
}