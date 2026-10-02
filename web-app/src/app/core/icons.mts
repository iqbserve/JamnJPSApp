/* Authored by iqbserve.de */

/**
 * <pre>
 * A module to centralize Font-Icon usage.
 * 
 * It provides icon metadata through function constants: 
 * Icons.<name>() named with the icon's identifying name.
 *  - Icons.<name>(): returns the icon's identifying name that is equal to <name>.
 *  - Icons.<name>("class"): returns the classes as a string
 * 
 * or by using the public info functions: 
 *  - getIconClasses(name) 
 *  - getIconShapeClasses(name) 
 *  - getIconClassString(name)
 * </pre>
 */

// bootstrap icon type classes
const BI_TypeClasses = ["bi"];
const GI_USE_CDN = false;
// google icon type classes
const GI_TypeClasses = GI_USE_CDN ? ["gi", "material-symbols-outlined"] : ["gi"];

// icon definitions container
const iconDefs: Record<string, IconDef> = {};

/**
 * <pre>
 * PUBLIC info functions
 * Same as Icons.<name>(<param>).
 * </pre>
 */
export function getIconClasses(name: string): string[] {
	return iconDefs[name].getIconClasses();
}

export function getIconShapeClasses(name: string): string[] {
	return iconDefs[name].getIconShapeClasses();
}

export function getIconClassString(name: string): string {
	if (iconDefs[name]) {
		return iconDefs[name].getIconClassString();
	}
	return "";
}


/**
 * INTERNALS
 */

/**
 * The icon constants function type definition.
 */
type IconFn = (opt?: "class" | null) => string;

/**
 */
function newIconConstant(name: string, shapes?: string[], typeClasses = BI_TypeClasses): IconFn {

	if (!iconDefs[name] && shapes) {
		iconDefs[name] = new IconDef(shapes, typeClasses);
	}

	const fn : IconFn = (opt: "class" | null = null) => {
		if (!opt) {
			return name;
		}  else if (opt === "class") {
			return iconDefs[name].getIconClassString();
		}
		return name;
	};
	return fn;
}

/**
 */
class IconDef {
	#shapes: string[];
	#typeClasses: string[];
	// only the direct icon class name
	#className: string;
	// the complete class string
	#classString: string;

	constructor(shapes: string[], typeClasses = BI_TypeClasses) {
		this.#className = shapes[0];
		this.#shapes = shapes;
		this.#typeClasses = typeClasses;

		this.#classString = this.#typeClasses.join(" ") + " " + this.#className;
	}

	getIconClassString(): string {
		return this.#classString;
	}

	getIconClasses(): string[] {
		return [...this.#typeClasses, this.#className];
	}

	getIconShapeClasses(): string[] {
		return this.#shapes;
	}
}

/**
 * Icon function CONSTANTS definitions
 */
export const arwExpand = newIconConstant("arwExpand", ["bi-arrows-expand", ""]);
export const arwCollapse = newIconConstant("arwCollapse", ["bi-arrows-collapse", ""]);
export const caretdown = newIconConstant("caretdown", ["bi-caret-down", ""]);
export const caretup = newIconConstant("caretup", ["bi-caret-up", ""]);
export const clipboardAdd = newIconConstant("clipboardAdd", ["bi-clipboard-plus", ""]);
export const close = newIconConstant("close", ["bi-x-lg", ""]);

export const collapse = newIconConstant("collapse", ["bi-chevron-bar-contract", "bi-chevron-bar-expand"]);
export const dlgmin = newIconConstant("dlgmin", ["bi-dash-lg", "bi-dash-lg"]);
export const hchevron = newIconConstant("hchevron", ["bi-chevron-up", "bi-chevron-down"]);

export const command = newIconConstant("command", ["bi-command", ""]);
export const dashCollapse = newIconConstant("dashCollapse", ["bi-dash-square", ""]);
export const dotmenu = newIconConstant("dotmenu", ["bi-three-dots-vertical", ""]);
export const eraser = newIconConstant("eraser", ["bi-eraser", ""]);

export const eye = newIconConstant("eye", ["bi-eye", "bi-eye-slash"]);
export const github = newIconConstant("github", ["bi-github", ""]);

export const info = newIconConstant("info", ["bi-info-square", "bi-info-square-fill"]);
export const infoc = newIconConstant("infoc", ["bi-info-circle", "bi-info-circle-fill"]);
export const login = newIconConstant("login", ["bi-person", "bi-person-check"]);
export const loginAction = newIconConstant("loginAction", ["bi-box-arrow-in-right", ""]);

export const menu = newIconConstant("menu", ["bi-list", ""]);
export const minusRemove = newIconConstant("minusRemove", ["bi-dash-square", ""]);
export const password = newIconConstant("password", ["bi-key", ""]);

export const pin = newIconConstant("pin", ["bi-pin", "bi-pin-angle"]);
export const plusNew = newIconConstant("plusNew", ["bi-plus-square", ""]);
export const pencilNew = newIconConstant("pencilNew", ["bi-pencil-square", ""]);
export const toggleExpand = newIconConstant("toggleExpand", ["bi-plus-square", "bi-dash-square"]);
export const question = newIconConstant("question", ["bi-question-square", "bi-question-square-fill"]);

export const redo = newIconConstant("redo", ["bi-arrow-counterclockwise", ""]);
export const run = newIconConstant("run", ["bi-caret-right-square", ""]);
export const save = newIconConstant("save", ["bi-floppy", ""]);
export const system = newIconConstant("system", ["bi-laptop", ""]);
export const tableSort = newIconConstant("tableSort", ["bi-arrow-down", "bi-arrow-up"]);

export const tools = newIconConstant("tools", ["bi-tools", ""]);
export const trash = newIconConstant("trash", ["bi-trash", ""]);
export const user = newIconConstant("user", ["bi-person", ""]);
export const wkvSidePanel = newIconConstant("wkvSidePanel", ["bi-arrow-bar-left", "bi-arrow-bar-right"]);
export const xRemove = newIconConstant("xRemove", ["bi-x-square", ""]);
export const filetypeJS = newIconConstant("filetypeJS", ["bi-filetype-js", ""]);

//google material icons
export const gi_system = newIconConstant("gi_system", ["gi-computer", ""], GI_TypeClasses);
export const gi_toggleExpand = newIconConstant("gi_toggleExpand", ["gi-expand-all", "gi-collapse-all"], GI_TypeClasses);


