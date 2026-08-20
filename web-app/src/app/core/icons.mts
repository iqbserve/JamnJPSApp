/* Authored by iqbserve.de */

/**
 * A module to centralize font icon usage.
 */

/**
 * PUBLIC
 */
export function getIconClasses(name: string): string[] {
	return iconClasses[name][0];
}

export function getIconShapeClasses(name: string): string[] {
	return iconClasses[name][1];
}

export function getIconClassString(name: string): string {
	return iconClasses[name][0].join(" ");
}

/**
 * Icon CONSTANTS as functions
 */
export const arwExpand = newConstantFunction("arwExpand");
export const arwCollapse = newConstantFunction("arwCollapse");
export const caretdown = newConstantFunction("caretdown");
export const caretup = newConstantFunction("caretup");
export const clipboardAdd = newConstantFunction("clipboardAdd");
export const close = newConstantFunction("close");
export const collapse = newConstantFunction("collapse");
export const dlgmin = newConstantFunction("dlgmin");
export const hchevron = newConstantFunction("hchevron");
export const command = newConstantFunction("command");
export const dashCollapse = newConstantFunction("dashCollapse");
export const dotmenu = newConstantFunction("dotmenu");
export const eraser = newConstantFunction("eraser");
export const eye = newConstantFunction("eye");
export const github = newConstantFunction("github");
export const info = newConstantFunction("info");
export const infoc = newConstantFunction("infoc");
export const login = newConstantFunction("login");
export const loginAction = newConstantFunction("loginAction");
export const menu = newConstantFunction("menu");
export const minusRemove = newConstantFunction("minusRemove");
export const password = newConstantFunction("password");
export const pin = newConstantFunction("pin");
export const plusNew = newConstantFunction("plusNew");
export const pencilNew = newConstantFunction("pencilNew");
export const toggleExpand = newConstantFunction("toggleExpand");
export const question = newConstantFunction("question");
export const redo = newConstantFunction("redo");
export const run = newConstantFunction("run");
export const save = newConstantFunction("save");
export const system = newConstantFunction("system");
export const tableSort = newConstantFunction("tableSort");
export const tools = newConstantFunction("tools");
export const trash = newConstantFunction("trash");
export const user = newConstantFunction("user");
export const wkvSidePanel = newConstantFunction("wkvSidePanel");
export const xRemove = newConstantFunction("xRemove");
export const filetypeJS = newConstantFunction("filetypeJS");

//google material icons
export const gi_system = newConstantFunction("gi_system");
export const gi_toggleExpand = newConstantFunction("gi_toggleExpand");

/**
 * INTERNALS
 */
function newConstantFunction(name: string) {
	return (opt = null) => {
		if (!opt) {
			return name;
		} else if (opt === "classes") {
			return getIconClasses(name);
		} else if (opt === "class") {
			return getIconClassString(name);
		}
		return name;
	};
}

/**
 * <name> = [[iconClasses], [shapeClasses]]
 * e.g. [["bi", "bi-person"], ["bi-person", "bi-person-check"]]
 */
function createClassDef(shapes: string[], typeClasses = BI_TypeClasses) {
	return Object.freeze([[...typeClasses, shapes[0]], Object.freeze(shapes)]);
}

const BI_TypeClasses = Object.freeze(["bi"]);
const GI_TypeClasses = Object.freeze(["gi", "material-symbols-outlined"]);

const iconClasses = Object.freeze({
	arwExpand: createClassDef(["bi-arrows-expand", ""]),
	arwCollapse: createClassDef(["bi-arrows-collapse", ""]),
	caretdown: createClassDef(["bi-caret-down", ""]),
	caretup: createClassDef(["bi-caret-up", ""]),
	clipboardAdd: createClassDef(["bi-clipboard-plus", ""]),
	close: createClassDef(["bi-x-lg", ""]),
	collapse: createClassDef(["bi-chevron-bar-contract", "bi-chevron-bar-expand"]),
	dlgmin: createClassDef(["bi-dash-lg", "bi-dash-lg"]),
	hchevron: createClassDef(["bi-chevron-up", "bi-chevron-down"]),
	command: createClassDef(["bi-command", ""]),
	dashCollapse: createClassDef(["bi-dash-square", ""]),
	dotmenu: createClassDef(["bi-three-dots-vertical", ""]),
	eraser: createClassDef(["bi-eraser", ""]),
	eye: createClassDef(["bi-eye", "bi-eye-slash"]),
	github: createClassDef(["bi-github", ""]),
	info: createClassDef(["bi-info-square", "bi-info-square-fill"]),
	infoc: createClassDef(["bi-info-circle", "bi-info-circle-fill"]),
	login: createClassDef(["bi-person", "bi-person-check"]),
	loginAction: createClassDef(["bi-box-arrow-in-right", ""]),
	menu: createClassDef(["bi-list", ""]),
	minusRemove: createClassDef(["bi-dash-square", ""]),
	password: createClassDef(["bi-key", ""]),
	pin: createClassDef(["bi-pin", "bi-pin-angle"]),
	plusNew: createClassDef(["bi-plus-square", ""]),
	pencilNew: createClassDef(["bi-pencil-square", ""]),
	toggleExpand: createClassDef(["bi-plus-square", "bi-dash-square"]),
	question: createClassDef(["bi-question-square", "bi-question-square-fill"]),
	redo: createClassDef(["bi-arrow-counterclockwise", ""]),
	run: createClassDef(["bi-caret-right-square", ""]),
	save: createClassDef(["bi-floppy", ""]),
	system: createClassDef(["bi-laptop", ""]),
	tableSort: createClassDef(["bi-arrow-down", "bi-arrow-up"]),
	tools: createClassDef(["bi-tools", ""]),
	trash: createClassDef(["bi-trash", ""]),
	user: createClassDef(["bi-person", ""]),
	wkvSidePanel: createClassDef(["bi-arrow-bar-left", "bi-arrow-bar-right"]),
	xRemove: createClassDef(["bi-x-square", ""]),
	filetypeJS: createClassDef(["bi-filetype-js", ""]),

	//google material icons
	gi_system: createClassDef(["gi-computer", ""], GI_TypeClasses),
	gi_toggleExpand: createClassDef(["gi-expand-all", "gi-collapse-all"], GI_TypeClasses)
});
