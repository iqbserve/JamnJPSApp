/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { NL, newSimpleId, FileDataReader, BackendServerUrl, OriginServerUrl } from 'core/tools.mjs';
import { WorkView, ViewDialog, SplitBarHandler, AttachmentHandler } from 'core/view-classes.mjs';
import { UIBuilder, onClicked, onChange } from 'core/uibuilder.mjs';
import { WorkViewHtml } from 'core/view-templates.mjs';
import * as Webapi from 'app/core/webapi.mjs';
import * as Icons from 'core/icons.mjs';

import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';
import { DataFile } from 'app/core/data-classes.mjs';

/* Types */
import type { JSObject } from 'types/commons';

function playgroundUrl(dest) {
	return BackendServerUrl(`/vres/playground/${dest}`);
}

const CodeEditor = {
	instance: null,

	defaultLanguage: "javascript",
	theme: "vs-dark", // Options: 'vs', 'vs-dark', 'hc-black'

	languageConfigs: {
		javascript: {
			comments: {
				lineComment: '//',
				blockComment: ['/*', '*/'],
			}
		}
	},

	keyBindings: [
		//{ key: (keymod, keycode) => { return keymod.CtrlCmd | keycode.KeyC }, command: "editor.action.commentLine" }
	]
};

/**
 */
class PlaygroundView extends WorkView {

	refId: string;

	scriptFileReader: FileDataReader;
	attachmentFileReader: FileDataReader;
	attachmentHandler: AttachmentHandler;

	scriptDataFile;
	lastRequest;

	//member objects to collect ui elements and ui objects from the builder
	elem: JSObject = {};
	uiobj: JSObject = {};

	//side panel 
	spMainPanel: HTMLElement;
	spDataPanel: HTMLElement;

	styles;

	previewDialog;
	iconPreview;

	constructor(id) {
		super(id, null);
		this.viewSource.setHtml(WorkViewHtml());
	}

	initialize() {
		super.initialize();
		this.setTitle("");

		this.refId = newSimpleId();
		PlainJSContext.refId = this.refId;

		this.viewHeader.menu((menu) => {
			menu.addItem("Open as Standalone window", () => {
				const url = OriginServerUrl(`/workbench.html?config=playground`);
				window.open(url, "jamn-workbench", "resizable=yes");
			}, { separator: "top" });
		});

		this.createUI();

		this.attachmentHandler = new AttachmentHandler(this.elem.lstAttachments);
		this.attachmentFileReader = new FileDataReader("text/*, .json, .txt", (dataFile: DataFile) => {
			this.attachmentHandler.addData(dataFile);
		});

		this.scriptFileReader = new FileDataReader(".js, .mjs", (dataFile: DataFile) => {
			this.setScript(dataFile);
		});

		this.isInitialized = true;
		this.setVisible(true);

		this.previewDialog = new PreviewDialog(this.viewElement);
		this.previewDialog.listener.push((dlg) => { this.onPreview(dlg) });

	}

	setTitle(titleInfo) {
		const title = `JavaScript Playground - [ ${titleInfo} ]`;
		super.setTitle(title);
	}

	/** START UI *****************************************************************/
	/**
	 */
	createUI() {

		const builder = new UIBuilder()
			//set the objects to hold all control dom elements with a varid
			.setElementCollection(this.elem)
			// and other things like e.g. datalists or "data-bind" infos
			.setObjectCollection(this.uiobj)
			//set default styles
			.setCompPropDefaults((props) => {
				props.apply(["label"], { styleProps: { "min-width": "80px", "max-width": "80px", "width": "80px" } });
			});

		this.styles = {
			taFontSize: "12px"
		};

		const comps: JSObject = {};
		this.createWorkareaLayout(builder, comps);

		this.createEditorComp(builder, comps.waMain);
		this.createEditor(this.elem.editorContainer);

		this.createSidePanel(builder);

		this.onModeChange();
	}

	/**
	 */
	createWorkareaLayout(builder, comps) {
		builder.newUICompFor(this.viewWorkarea)
			.addColContainer({ elemType: "div", clazzes: ["flex-one"] }, (waMain) => {
				waMain.style({ height: "100%", gap: "10px" })
				comps.waMain = waMain;
			})
	}

	/**
	 */
	createEditorOptionsComp(builder, target) {
		builder.newUIComp()
			.addLabel({ elemType: "labelText", text: "Options:" })
			.addGroup({}, (group) => {
				group.style({ "flex-direction": "row", "margin-bottom": "0px" })
					.addRadioButton({ varid: "rbSnippetMode", name: "modeGroup", value: "snippetMode" }, (rb) => {
						rb.style({ "margin-right": "10px" });
						onChange(rb, () => { this.onModeChange() });
					})
					.addLabel({ text: "Snippet mode", title: this.getText("snippetMode"), name: "lbSnippetMode" }, (lb) => {
						lb.style({ "min-width": "fit-content", "padding-right": "15px", "border-right": "1px solid var(--border-gray)" })
							.linkToElement(this.elem.rbSnippetMode)
					})
					.addRadioButton({ varid: "rbModuleMode", name: "modeGroup", value: "moduleMode" }, (rb) => {
						rb.style({ "margin-right": "10px" });
						onChange(rb, () => { this.onModeChange() });
					})
					.addLabel({ text: "Module mode", title: this.getText("moduleMode"), name: "lbModuleMode" }, (lb) => {
						lb.style({ "min-width": "fit-content" })
							.linkToElement(this.elem.rbModuleMode)
					})
			})

			.addCheckBox({ varid: "cbKeep" }, (cb) => { cb.style({ "margin-right": "10px" }) })
			.addLabel({ text: "Debug keep", title: this.getText("keep"), name: "lbKeep" }, (lb) => {
				lb.style({ "min-width": "fit-content", "margin-right": "15px" })
					.linkToElement(this.elem.cbKeep)
			})
			.appendTo(target.getDomElem());

		this.elem.rbSnippetMode.checked = true;
	}

	/**
	 */
	createEditorRunComp(builder, target) {
		builder.newUIComp()
			.addLabelButton({ text: "Run:" },
				{ varid: "pbRun", iconName: Icons.run(), text: "editor code", title: "Run script code" }, (label, pbRun) => {
					onClicked(pbRun, () => { this.runCurrentCode() });
				})
			.addTextField({ varid: "tfRunMethod", datalist: ["main", "run", "default", " "] }, (tf) => {
				tf.style({ "margin-left": "15px", width: "150px" }).attrib({ placeholder: "method: main, run, ... etc.", title: "Method to be called for running the Module" });
			})
			.addRowContainer((comp) => {
				comp.style({ "margin-left": "20px", "padding-left": "15px", "border-left": "1px solid var(--border-gray)", gap: "15px", "align-items": "center" })
					.addActionIcon({ varid: "icoScriptSource", iconName: Icons.filetypeJS(), title: "Open/load a local JavaScript File (*.js, *.mjs) ..." }, (icon) => {
						icon.style({ "font-size": "26px" })
						onClicked(icon, () => { this.scriptFileReader.chooseFile() });
					})
					.addActionIcon({ varid: "icoCodeSave", iconName: Icons.save(), title: "Save current editor code to file ..." }, (icon) => {
						onClicked(icon, () => { this.saveEditorCode(); });
					})
			})


			.addRowContainer((iconBar) => {
				iconBar.style({ "margin-left": "40px", "align-items": "center", "gap": "15px" })
					.addActionIcon({ varid: "icoEditorCodeToClipboard", iconName: Icons.clipboardAdd(), title: "Copy code to clipboard" }, (icon) => {
						onClicked(icon, () => { this.copyEditorCodeToClipboard(); });
					})
					.addActionIcon({ varid: "icoClearCode", iconName: Icons.pencilNew(), title: "New script - Clear/Delete entire editor content" }, (icon) => {
						onClicked(icon, () => { this.askForCodeClearing(); });
					})
			})

			.appendTo(target.getDomElem());
	}

	/**
	 */
	createEditorComp(builder, target) {

		this.createEditorOptionsComp(builder, target);
		this.createEditorRunComp(builder, target);

		let splitterElem;

		builder.newUIComp()
			.style({ "align-items": "flex-start", width: "100%" })
			.addColContainer((comp) => {
				comp.style({ width: "100%", "overflow-x": "auto", border: "1px solid var(--border-gray)", "margin-bottom": "20px" })
					.addDiv({ varid: "editorContainer" }, (div) => {
						div.style({ "height": "300px", "min-height": "200px" })
					})
					.addDiv({ clazzes: ["splitter", "hsplit"] }, (splitter) => {
						splitterElem = splitter.style({ width: "100%", "border-bottom": "1px solid var(--border-gray)" }).domElem;
					})
			})
			.appendTo(target.getDomElem());

		new SplitBarHandler(splitterElem)
			.setCompBefore(this.elem.editorContainer)
			.setToHorizontalOrientation()
			.build();
	}

	/**
	 */
	createEditor(editorContainer) {
		const stopRunningMode = () => { this.setRunning(false); this.setDisabled(false); this.setTitle(""); };
		this.setTitle("Loading external Editor ...");
		this.setRunning(true);
		this.setDisabled(true);

		// eslint-disable-next-line @typescript-eslint/ban-ts-comment
		// @ts-ignore
		const requireObj = window.require;

		// configure the path to the VS Code worker scripts
		requireObj.config({ paths: { 'vs': WbProperties.get("vcUrls").config } });

		// load the editor module and create the instance
		requireObj(['vs/editor/editor.main'], function () {
			try {
				// eslint-disable-next-line @typescript-eslint/ban-ts-comment
				// @ts-ignore
				const monacoObj = window.monaco;

				// config editor languages
				for (const name in CodeEditor.languageConfigs) {
					monacoObj.languages.setLanguageConfiguration(name, CodeEditor.languageConfigs[name]);
				}

				// create editor instance
				CodeEditor.instance = monacoObj.editor.create(editorContainer, {
					value: textCollection["newSnippet"],
					language: CodeEditor.defaultLanguage,
					theme: CodeEditor.theme,
					automaticLayout: true // Resizes the editor if the window changes size
				});

				// create user key bindings
				CodeEditor.keyBindings.forEach((binding) => {
					CodeEditor.instance.addCommand(binding.key(monacoObj.KeyMod, monacoObj.KeyCode), () => {
						CodeEditor.instance.trigger('editor', binding.command, null);
					});
				});
			} catch (e) {
				Logger.error(e);
			} finally {
				stopRunningMode();
			}
		});
	}

	/**
	 * Start building Sidepanel
	 */
	createSidePanel(builder) {
		this.installSidePanel(null).setWidth("350px");
		//show it opened
		this.toggleSidePanel();

		const comps: JSObject = {};
		this.createSPanelMainLayout(builder, comps);
		this.createSPanelHeadComp(builder, comps.sidePanelHead);

		this.createSPDataPanel(builder);

		this.sidePanel.setViewComp(this.spMainPanel);
	}

	/**
	 */
	createSPanelMainLayout(builder, comps) {
		const panelComp = builder.newUIComp("blankComp").class(["col-comp"]).attrib({ name: "sidePanelMain" });

		this.spMainPanel = panelComp
			.addRowContainer({ elemType: "div", name: "sidePanelHead" }, (head) => {
				head.style({ "flex-direction": "row-reverse", height: "20px", overflow: "hidden", "border-bottom": "1px solid var(--border-gray)" });
				comps.sidePanelHead = head;
			})
			.getDomElem();
	}

	/**
	 */
	createSPanelHeadComp(builder, target) {
		builder.newUICompFor(target.domElem)
			.addRowContainer({ varid: "spanelIconBar" }, (iconBar) => {
				iconBar.style({ "margin-right": "10px", "align-items": "center" })
				iconBar.addActionIcon({ varid: "icoOpenPreview", iconName: Icons.eye(), title: "Show/Hide Preview panel" }, (icon) => {
					onClicked(icon, () => this.togglePreview());
				})
			});
	}

	/**
	 */
	createSPDataPanel(builder) {
		const comps: JSObject = {};
		this.createSPDataPanelLayout(builder, comps)
		this.createSPDataPanelArgsComp(builder, comps.dataPanelTop);
		this.createSPDataPanelAttachmentsComp(builder, comps.dataPanelTop);
		this.createSPDataPanelOutputComp(builder, comps.dataPanelBottom);

		this.spMainPanel.append(this.spDataPanel);
	}

	/**
	 */
	createSPDataPanelLayout(builder, comps) {
		let splitterElem;

		const panelComp = builder.newUIComp("blankComp").class(["col-comp"]).attrib({ name: "spDataPanel" });

		this.spDataPanel = panelComp
			.addColContainer({ elemType: "div", name: "dataPanelTop" }, (top) => {
				top.style({ gap: "10px", margin: "5px", overflow: "hidden" });
				comps.dataPanelTop = top;
			})
			.addDiv({ clazzes: ["splitter", "hsplit"] }, (splitter) => {
				splitterElem = splitter
					.style({ width: "100%", "margin-top": "10px", "margin-bottom": "10px", "border-top": "2px solid var(--border-gray)" }).domElem;
			})
			.addColContainer({ elemType: "div", name: "dataPanelBottom" }, (bottom) => {
				bottom.style({ gap: "10px", margin: "5px" })
				comps.dataPanelBottom = bottom;
			})
			.getDomElem();

		new SplitBarHandler(splitterElem)
			.setCompBefore(comps.dataPanelTop.domElem)
			.setCompAfter(comps.dataPanelBottom.domElem)
			.setToHorizontalOrientation()
			.build();
	}

	/**
	 */
	createSPDataPanelArgsComp(builder, target) {
		let argsLabel;
		builder.newUIComp("colComp")
			.addLabel({ text: "Args:", name: "lbArgs" }, (label) => { argsLabel = label })
			.addTextArea({ varid: "taArgs" }, (textarea) => {
				textarea.class("wkv-args-textarea-ctrl")
					.title("Arguments")
					.style({ resize: "none", "height": "45px", "text-align": "left", width: "auto", "margin-top": "5px" })
					.attrib({ placeholder: "<no args>", autocorrect: "off", autocapitalize: "off", spellcheck: false })
					.linkToLabel(argsLabel);
			})
			.appendTo(target.getDomElem());
	}

	/**
	 */
	createSPDataPanelAttachmentsComp(builder, target) {
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabel({ elemType: "labelText", text: "Attachments:", name: "lbAttachments" })
			.addRowContainer((iconBar) => {
				iconBar.style({ "align-items": "center", "justify-content": "left", "gap": "15px" })
					.addActionIcon({ varid: "icoRemoveAllAttachments", iconName: Icons.trash(), title: "Remove all Attachments" })
					.addActionIcon({ varid: "icoAddAttachment", iconName: Icons.plusNew(), title: "Add Attachment" });
			})
			.appendTo(target.getDomElem());

		builder.newUIComp()
			.addList({ varid: "lstAttachments" }, (list) => {
				list.style({ "width": "100%", "padding": "10px", "margin": "0" })
			})
			.appendTo(target.getDomElem());

		onClicked(this.elem.icoAddAttachment, () => { this.attachmentFileReader.chooseFile() });
		onClicked(this.elem.icoRemoveAllAttachments, () => { this.attachmentHandler.removeAllData() });
	}

	/**
	 */
	createSPDataPanelOutputComp(builder, target) {
		let outputLabel;
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabel({ text: "Output:", name: "lbOutput" }, (label) => { outputLabel = label })
			.addRowContainer((iconBar) => {
				iconBar.style({ "align-items": "center", "gap": "15px", "flex-direction": "row-reverse" })
					.addActionIcon({ varid: "icoOutputSave", iconName: Icons.save(), title: "Save current output to a file" }, (icon) => {
						onClicked(icon, () => { this.saveOutput(); });
					})
					.addActionIcon({ varid: "icoOutputToClipboard", iconName: Icons.clipboardAdd(), title: "Copy current output to clipboard" }, (icon) => {
						onClicked(icon, () => { this.copyOutputToClipboard(); });
					})
					.addActionIcon({ varid: "icoOutputDelete", iconName: Icons.trash(), title: "Delete current output" }, (icon) => {
						onClicked(icon, () => { this.clearOutput(); });
					});
			})
			.appendTo(target.getDomElem());

		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addTextArea({ varid: "taOutput", title: "Script Output" }, (textarea) => {
				textarea.class("wkv-output-textarea-ctrl").attrib({ disabled: true, autocorrect: "off", autocapitalize: "off", spellcheck: false })
					.style({ resize: "vertical", "min-width": "unset", "min-height": "50px", height: "200px", "text-align": "left", "font-size": this.styles.taFontSize })
					.linkToLabel(outputLabel);
			})
			.appendTo(target.getDomElem());
	}

	/** END UI *****************************************************************/

	toggleCollapsed() {
		if (super.toggleCollapsed()) {
			if (this.previewDialog.isOpen()) {
				this.previewDialog.close();
			}
		}
		return this.state.isCollapsed;
	}

	togglePreview() {
		if (this.previewDialog.isOpen()) {
			this.previewDialog.close();
		} else {
			this.previewDialog.open();
		}
	}

	onPreview(dlg) {
		this.elem.icoOpenPreview.switch({ flag: dlg.isOpen() });
	}

	/**
	 */
	setRunning(flag) {
		super.setRunning(flag);
		this.elem.pbRun.disabled = flag;
	}

	/**
	 */
	setScript(scriptFile) {
		if (scriptFile) {
			this.scriptDataFile = scriptFile;
			this.elem.pbRun.innerHTML = this.scriptDataFile.name;
			this.elem.icoScriptSource.style.color = "green";
			CodeEditor.instance.setValue(this.scriptDataFile.data);
			if (this.scriptDataFile.name.endsWith(".mjs")) {
				this.elem.rbModuleMode.click();
			} else {
				this.elem.rbSnippetMode.click();
			}
		} else {
			this.scriptDataFile = null;
			this.elem.pbRun.innerHTML = "editor code";
			this.elem.icoScriptSource.style.color = "";
		}
	}

	/**
	 */
	onModeChange() {
		if (this.hasSnippetMode()) {
			this.elem.cbKeep.checked = false;
			this.elem.cbKeep.disabled = true;
			this.elem.tfRunMethod.disabled = true;
			this.elem.tfRunMethod.value = "";
		} else {
			this.elem.cbKeep.disabled = false;
			this.elem.tfRunMethod.disabled = false;
		}
	}

	/**
	 */
	hasSnippetMode() {
		return this.elem.rbSnippetMode.checked;
	}

	/**
	 */
	getCurrentCode() {
		return CodeEditor.instance.getValue();
	}

	/**
	 */
	getCurrentArgs() {
		return this.elem.taArgs.value.trim();
	}

	/**
	 */
	getCurrentAttachments() {
		return this.attachmentHandler.getData();
	}

	/**
	 */
	runCurrentCode() {
		try {
			this.setRunning(true);
			if (this.hasSnippetMode()) {
				this.executeSnippetMode();
			} else {
				this.executeModuleMode();
			}
		} catch (e) {
			echo(e);
		} finally {
			this.setRunning(false);
		}
	}

	/**
	 */
	executeSnippetMode() {
		const executor = (context, scriptCode) => {
			return new Function(`"use strict";\n${scriptCode}`).bind(context);
		}
		executor(PlainJSContext, this.getCurrentCode())(this.getCurrentArgs(), this.getCurrentAttachments());
	}

	/**
	 */
	executeModuleMode() {
		let request = null;
		const runMethod = this.elem.tfRunMethod.value.trim();
		const scriptSource = this.getCurrentCode();

		if (this.elem.cbKeep.checked && this.lastRequest) {
			request = this.lastRequest;
		} else {
			request = new PlaygroundRequest(this.refId, newSimpleId());
			request.addContent("mjs", scriptSource);
			this.lastRequest = request;
		}

		Webapi.doPOST(Webapi.service_upload_playground_content, request.toJson()).then(() => {
			import(playgroundUrl(`playground-run-code.mjs?id=${request.contentId}`))
				.then((module) => {
					if (runMethod && module[runMethod]) {
						module[runMethod](this.getCurrentArgs(), this.getCurrentAttachments());
					} else if (runMethod) {
						echo(`Run method [${runMethod}] NOT present in module`);
					} else {
						echo(`Current module source loaded - no explicite method executed`);
					}
				})
				.catch((e) => {
					echo(e);
				});
		});
	}

	/**
	 */
	saveOutput() {
		const fileName = "output_" + (this.scriptDataFile ? this.scriptDataFile.name : "playground") + ".txt";
		this.saveToFile(fileName, this.elem.taOutput.value.trim());
	}

	/**
	 */
	copyOutputToClipboard() {
		this.copyToClipboard(this.elem.taOutput.value.trim());
	}

	/**
	 */
	saveEditorCode() {
		const type = this.hasSnippetMode() ? "js" : "mjs";
		let fileName = `playground-script.${type}`;
		if (this.scriptDataFile) {
			fileName = this.scriptDataFile.name;
			this.scriptDataFile.data = this.getCurrentCode();
		}
		this.saveToFile(fileName, this.getCurrentCode());
	}

	/**
	 */
	copyEditorCodeToClipboard() {
		this.copyToClipboard(this.getCurrentCode());
	}

	/**
	 */
	askForCodeClearing() {
		WbApp.confirm({
			message: "<b>Create a new script</b><br>Do you want to delete/replace the entire editor content? <br>This can NOT be undone. <br><br>This action ONLY applies to the editor content."
		}, (yes) => yes ? this.clearCode() : null);
	}

	/**
	 */
	clearCode() {
		this.setScript(null);
		if (this.hasSnippetMode()) {
			CodeEditor.instance.setValue(this.getText("newSnippet"));
		} else {
			CodeEditor.instance.setValue(this.getText("newModule"));
			this.elem.tfRunMethod.value = "main";
		}
	}

	/**
	 */
	outputScrollTop() {
		this.elem.taOutput.scrollTop = this.elem.taOutput.scrollHeight;
	}

	/**
	 */
	addOutputLine(line) {
		this.elem.taOutput.value += NL + line;
		this.outputScrollTop();
	}

	/**
	 */
	clearOutput() {
		if (!this.state.isRunning) {
			const lastValue = this.elem.taOutput.value;
			this.elem.taOutput.value = "";
			return lastValue;
		}
		return "";
	}

	/**
	 */
	echoToOutput(obj) {
		if (obj) {
			this.addOutputLine(obj.toString());
		}
	}

	/**
	 */
	getText(id) {
		return textCollection[id];
	}
}

/**
 */
class PlaygroundRequest {
	clientId;
	contentId;
	keep = false;
	content = {};

	constructor(clientId, contentId) {
		this.clientId = clientId;
		this.contentId = contentId;
	}

	addContent(type, value) {
		this.content[type] = value;
	}

	toJson() {
		return JSON.stringify(this);
	}
}

/**
 * export this view component as singleton instance
 */
const viewInstance = new PlaygroundView("jsPlaygroundView");
export function getView() {
	return viewInstance;
}

/**
 * export execution context, functions and objects
 */
export function echo(obj, json = false) {
	Logger.consoleLog(obj);
	if (json && obj) {
		viewInstance.echoToOutput(JSON.stringify(obj));
	} else if (obj) {
		viewInstance.echoToOutput(obj);
	}
};

export let previewComp = null; // NOSONAR

//execution context for plain js snippets
const PlainJSContext = {
	refId: "",
	previewComp: null,
	echo: echo
};


/**
 */
class PreviewDialog extends ViewDialog {

	constructor(parent) {
		super();
		this.parentElem = parent;
		this.initialize();
	}

	initialize() {
		this.createDialogElement();
		this.createDefaultContentContainer();

		this.setTitle("UI Preview");
		this.closeIcon.attrib({ title: "Hide Preview" });
		this.content.style({ "background": "var(--workarea-bg)" });
		this.iconbar
			.style({ display: "" })
			.addActionIcon({ varid: "clearIcon", iconName: Icons.trash(), title: "Clear Preview" }, (icon) => {
				icon.class(["dlg-header-action-icon"]);
				onClicked(icon, () => {
					this.createPreviewComp();
				});
			})

		this.initDragging();
		this.initResizing();

		this.createPreviewComp();

		this.isInitialized = true;
	}

	createPreviewComp() {
		const builder = new UIBuilder();

		if (previewComp && this.viewArea.domElem.contains(previewComp.domElem)) {
			previewComp.domElem.remove();
		}

		previewComp = builder.newUIComp("blankComp").class([]).style({}).attrib({ name: "PreviewPanel" });
		PlainJSContext.previewComp = previewComp;

		this.viewArea.domElem.append(previewComp.domElem);
	}

}

/**
 * a text collection 
 */
const textCollection = {
	keep: `
Keep for Debugging - if checked
the local code file is preserved so it can be found in dev tools
e.g. to toggle debug breakpoints
in keep-mode NO editor code changes are reflected any more
`,

	snippetMode: `
In Snippet-Mode
the code gets executed as a sandbox function
this allows to run simple js snippets quick an easy
but there is NO module and NO run method support
`
	,

	moduleMode: `
In Module-Mode
the code gets loaded and executed as a JavaScript Module
this allows the import of other modules.
Furthermore a run method can be defined
`,

	newSnippet: `
// startup snippet
// this.echo('New JavaScript');
`,

	newModule: `
// startup snippet
// import { echo } from "features/js-playground.mjs";
//
// export const main = (args, attachments) => {
//     echo("New ModuleScript");
// };
`
}