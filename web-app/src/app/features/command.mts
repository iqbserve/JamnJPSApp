/* Authored by iqbserve.de */

import { NL, newSimpleId, asDurationString, FileDataReader } from 'core/tools.mjs';
import { WsoCommonMessage, CommandDef, DataFile } from 'core/data-classes.mjs';
import { WorkView, AttachmentHandler } from 'core/view-classes.mjs';
import { UIBuilder, onClicked, onKeydown, KEY } from 'core/uibuilder.mjs';
import { WorkViewHtml } from 'core/view-templates.mjs';
import * as Icons from 'core/icons.mjs';

import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';

/* Types */
import type { DynamicFunction, ExtenderFunction, FncArgs, JSObject } from 'types/commons';
import { SimpleCrudComp } from 'app/core/uicomponents.mjs';

/**
 * A general View class for server side commands.
 * 
 * Commands are either server side JavaScripts or java extension classes.
 * The communication is done via websocket.
 * 
 * The client side command definitions are located in:
 *  - wb-features.mjs
 * The web socket counterpart on the server-side is:
 *  - org.isa.jps.comp.JPSWebSocketMessageProcessor
 * The server side JS scripts are located in subdir
 *  - "scripts"
 * The server side java extensions are located in subdir
 *  - "extensions"
 * 
 */
export class CommandView extends WorkView {
	//websocket communication ref id
	wsoRefId: string;

	commandDef: CommandDef;
	commandName: string;
	runTime: number;
	duration: number;
	namedArgs: { [key: string]: string; } = { none: "" };
	ArgsComp: SimpleCrudComp;

	//input element for file dialog
	attachmentFileReader: FileDataReader;
	attachmentHandler: AttachmentHandler;

	//ui element collections
	elem: JSObject = {};
	uiobj: JSObject = {};

	outputProps = { initWidth: "1000px", initHeight: "300px", hstep: 50, steps: 0 };

	//function to extend this general command view
	//with feature specifics
	viewExtender: DynamicFunction;

	constructor(id: string, cmdDef: CommandDef, viewExtender: DynamicFunction = null) {
		super(id, null);
		this.commandDef = cmdDef;
		this.commandName = this.commandDef.command + " " + this.commandDef.script;
		this.viewSource.setHtml(WorkViewHtml());
		this.viewExtender = viewExtender;
	}

	initialize() {
		super.initialize();
		this.setTitle(this.commandDef.title);

		//just demo data
		this.namedArgs = { help: "-h", testfile: "-file=test-data.json", cdata: '<![CDATA[ {"name":"HelloFunction", "args":["John Doe"]} ]]>' };

		this.viewHeader.menu((menu) => {
			menu.addItem("Clear Output", () => {
				this.clearOutput(true);
			}, { separator: "top" })
				.addItem("Clear View", () => {
					this.clearAll();
				});
		});

		this.createUI();

		this.attachmentHandler = new AttachmentHandler(this.elem.lstAttachments);
		this.attachmentFileReader = new FileDataReader("text/*, .json, .txt", (dataFile: DataFile) => {
			this.attachmentHandler.addData(dataFile);
		});

		this.createWsoConnection();

		//extend the view with feature specifics
		this.viewExtender?.invoke((extend: ExtenderFunction<CommandView>) =>
			extend(this)
		);

		this.isInitialized = true;
		this.setVisible(true);
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
				props.apply(["label"], { styleProps: { "width": "80px" } });
			});

		//create a fieldset as component container in the view workarea
		let compSet;
		builder.newUICompFor(this.viewWorkarea)
			.addFieldset((comp) => {
				comp.style({ "margin-top": "10px", "gap": "10px" });
				compSet = comp.getDomElem();
			});

		//the run button
		builder.newUIComp()
			.addLabelButton({ text: "Command:" },
				{ varid: "pbRun", iconName: Icons.run(), text: this.commandName, title: "Run command" }, (label, pbRun) => {
					onClicked(pbRun, () => { this.runCommand() });
				})
			.appendTo(compSet);

		this.createArgsSection(builder, compSet);
		this.createAttachmentsSection(builder, compSet);

		//a separator
		builder.newUIComp().addSeparator((elem) => { elem.style({ width: "100%" }) }).appendTo(compSet);

		this.createOutputSection(builder, compSet);
	}

	/**
	 */
	createArgsSection(builder, target) {
		const taTitle = (this.commandDef.options.args ? "Command arguments: -h for help" : "<no args>") + "\nStructured text like e.g. JSON must be wrapped in a <![CDATA[ structured text ]]> tag.";
		const taPlaceholder = this.commandDef.options.args ? " -h + Enter for help" : "<no args>";
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabelTextArea({ text: "Args:" }, { varid: "taArgs" }, (label, textarea) => {
				textarea
					.title(taTitle)
					.style({ "width": "400px", "min-width": "400px", "height": "45px", "min-height": "45px", "text-align": "left" })
					.attrib({ placeholder: taPlaceholder, disabled: !this.commandDef.options.args });
				onKeydown(textarea, (evt) => {
					if (this.commandDef.options.args) {
						if (KEY.isEnter(evt) && evt.currentTarget.value.trim() === "-h") {
							this.runCommand();
						}
					}
				});
			})
			.add({ elemType: SimpleCrudComp.TagName }, (comp) => {
				this.ArgsComp = comp.domElem.build(builder)
					.setItems(Object.getOwnPropertyNames(this.namedArgs))
					.setStyle({ "margin-left": "20px", "gap": "5px", "align-items": "end" })
					.setBarPosition("bottom")
					.setFieldWidth("200px")
					.displayLabel(false)
					.setPlaceholder("named args")
					.setSelectionChangedAction(() => {
						this.setArgsSelection();
					})
					.setClearAction(() => {
						this.clearArgChoice();
					}, "Clear current selection and data")
					.setSaveAction(() => {
						this.saveArgChoice();
					}, "Save current named arg")
					.setDeleteAction(() => {
						this.deleteArgChoice();
					}, "Delete current named arg");
			})
			.appendTo(target);
	}

	/**
	 */
	createAttachmentsSection(builder, target) {
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabel({ text: "Attachments:", elemType: "label-text" })
			.addColContainer((attachBox) => {
				attachBox
					.addRowContainer((iconBar) => {
						iconBar.style({ gap: "20px", "align-self": "flex-start" })
							.addActionIcon({ varid: "icoRemoveAllAttachments", iconName: Icons.trash(), title: "Remove all Attachments" })
							.addActionIcon({ varid: "icoAddAttachment", iconName: Icons.plusNew(), title: "Add Attachment" });
					}).addList({ varid: "lstAttachments" }, (list) => {
						list.style({ "min-width": "385px", "min-height": "20px", "padding": "10px" })
					});
			})
			.appendTo(target);

		onClicked(this.elem.icoAddAttachment, () => { this.attachmentFileReader.chooseFile() });
		onClicked(this.elem.icoRemoveAllAttachments, () => { this.clearAttachments(true) });
	}

	/**
	 */
	createOutputSection(builder, target) {
		let lbOutput;

		builder.newUIComp()
			.style({ "flex-direction": "column", width: "fit-content", "align-items": "flex-start" })

			.addColContainer((comp) => {
				comp.addRowContainer((head) => {
					head.style({ "margin-bottom": "10px" })
						.addLabel({ text: "Output:", name: "lbOutput" }, (label) => {
							lbOutput = label;
							label.style({ "min-width": "80px" })
						})
						.addRowContainer((iconBar) => {
							iconBar.style({ gap: "15px" })
								.addActionIcon({ varid: "icoOutputDelete", iconName: Icons.trash(), title: "Delete current output" }, (icon) => {
									onClicked(icon, () => { this.clearOutput(true); });
								})
								.addActionIcon({ varid: "icoOutputToClipboard", iconName: Icons.clipboardAdd(), title: "Copy current output to clipboard" }, (icon) => {
									onClicked(icon, () => { this.copyOutputToClipboard(); });
								})
								.addActionIcon({ varid: "icoOutputSave", iconName: Icons.save(), title: "Save current output to a file" }, (icon) => {
									onClicked(icon, () => { this.saveOutput(); });
								});
						})

						.addRowContainer((iconBar) => {
							iconBar.style({ "flex-direction": "row-reverse", gap: "15px", width: "100%", "padding-right": "10px" })
								.addActionIcon({ varid: "icoOutputExpand", iconName: Icons.arwExpand(), title: "Expand height" }, (icon) => {
									onClicked(icon, () => { this.expandOutput() });
								})
								.addActionIcon({ varid: "icoOutputReduce", iconName: Icons.arwCollapse(), title: "Reduce height" }, (icon) => {
									onClicked(icon, () => { this.reduceOutput() });
								})
						})
				});

				comp.addTextArea({ varid: "taOutput" }, (taOutput) => {
					taOutput.class("wkv-output-textarea-ctrl").style({ width: this.outputProps.initWidth, height: this.outputProps.initHeight, "min-width": "490px" }).attrib({ disabled: true })
						.linkToLabel(lbOutput);
				})
			})
			.appendTo(target);
	}

	/** END UI *****************************************************************/

	/**
	 */
	createWsoConnection() {
		this.wsoRefId = newSimpleId(this.id + ":");
		WbApp.addWsoMessageListener((wsoMsg) => {

			if (wsoMsg.hasReference(this.wsoRefId)) {
				if (wsoMsg.hasStatusSuccess()) {
					this.setRunning(false);
					this.addOutputLine(NL + `Command finished: [${wsoMsg.status}] [${this.commandName}] [${asDurationString(this.runTime)}]`);
					this.addOutputLine(NL + "Return value:" + NL);
					this.addOutputLine(wsoMsg.bodydata);
				} else if (wsoMsg.hasStatusError()) {
					this.addOutputLine(NL + wsoMsg.error);
					this.setRunning(false);
				} else {
					this.addOutputLine(wsoMsg.bodydata);
				}
			} else if (wsoMsg.hasStatusError && wsoMsg.error.includes("connection")) {
				this.addOutputLine(NL + wsoMsg.error);
				this.setRunning(false);
			} else if (wsoMsg.hasStatusError && wsoMsg.hasReference("server.global")) {
				this.addOutputLine(NL + "WebSocket Error [" + wsoMsg.error + "] the central connection was closed.");
				this.setRunning(false);
			}
		});
	}

	/**
	 */
	clearAll() {
		this.clearArgChoice();
		this.clearAttachments();
		this.clearOutput(true);

		//resize elements
		this.elem.taArgs.style.width = "0px";
		this.elem.taArgs.style.height = "0px";
		this.elem.taOutput.style.width = this.outputProps.initWidth;
		this.elem.taOutput.style.height = this.outputProps.initHeight
	}

	/**
	 */
	clearOutput(ask = false) {
		const taOutput = this.elem.taOutput as HTMLTextAreaElement;
		if (!this.state.isRunning && taOutput.value.toString().trim()) {
			const clear = () => {
				const lastValue = taOutput.value;
				taOutput.value = "";
				return lastValue;
			};
			if (ask) {
				WbApp.confirm({
					message: `<b>Delete output</b><br>Do you want to delete the current output?`
				}, (val) => {
					if (val) { return clear(); }
				});
			} else {
				return clear();
			}
		}
		return "";
	}

	/**
	 */
	clearAttachments(ask = false) {
		if (this.attachmentHandler.hasData()) {
			if (ask) {
				WbApp.confirm({
					message: `<b>Remove all attachments</b><br>Do you want to remove all current attachments?`
				}, (val) => {
					if (val) { this.attachmentHandler.removeAllData(); }
				});
			} else {
				this.attachmentHandler.removeAllData();
			}
		}
	}

	/**
	 */
	clearArgChoice() {
		this.elem.taArgs.value = "";
		this.ArgsComp.setFieldValue("");
	}

	/**
	 */
	setRunning(flag) {
		super.setRunning(flag);

		this.elem.pbRun.disabled = flag;
		if (flag) {
			this.runTime = Date.now();
		} else {
			this.runTime = Date.now() - this.runTime;
		}
	}

	/**
	 */
	runCommand() {
		const wsoMsg = new WsoCommonMessage(this.wsoRefId);
		wsoMsg.command = this.commandDef.command;
		wsoMsg.functionModule = this.commandDef.script;
		wsoMsg.argsSrc = this.elem.taArgs.value.trim();

		if (this.attachmentHandler.hasData()) {
			this.attachmentHandler.getData().forEach((dataFile) => {
				wsoMsg.addAttachment(dataFile.name, dataFile.data);
			})
		}

		this.clearOutput();
		WbApp.sendWsoMessage(wsoMsg, () => {
			this.setRunning(true);
		});
	}

	/**
	 */
	addOutputLine(line) {
		this.elem.taOutput.value += line + NL;
		this.elem.taOutput.scrollTop = this.elem.taOutput.scrollHeight;
	}

	/**
	 */
	setArgsSelection() {
		const key = this.ArgsComp.getFieldValue();
		if (this.commandDef.options.args && this.namedArgs[key]) {
			this.elem.taArgs.value = this.namedArgs[key];
		}
	}

	/**
	 */
	getDataListObjFor(name) {
		return this.uiobj[this.elem[name].list.id];
	}

	/**
	 */
	saveArgChoice() {
		const key = this.ArgsComp.getFieldValue();
		if (key != "") {
			this.namedArgs[key] = this.elem.taArgs.value.trim();
			this.ArgsComp.addItem(key);
		}
	}

	/**
	 */
	deleteArgChoice() {
		const key = this.ArgsComp.getFieldValue();

		if (this.namedArgs[key]) {
			WbApp.confirm({
				message: `<b>Delete entry</b><br>Do you want to delete named arg <b>[${key}]</b> ?`
			}, (val) => {
				if (val) {
					delete this.namedArgs[key];
					this.ArgsComp.removeItem(key);
					this.clearArgChoice();
				}
			});
		}
	}

	/**
	 */
	saveOutput() {
		const fileName = "output_" + (this.commandDef.command + "_" + this.commandDef.script).replaceAll("/", "_") + ".txt";
		this.saveToFile(fileName, this.elem.taOutput.value.trim());
	}

	/**
	 */
	copyOutputToClipboard() {
		this.copyToClipboard(this.elem.taOutput.value.trim());
	}

	expandOutput() {
		const props = this.outputProps;
		props.steps = ++props.steps;
		const height = (props.steps * props.hstep).toString() + "vh";
		this.elem.taOutput.style.height = height;
	}

	reduceOutput() {
		const props = this.outputProps;
		props.steps = props.steps > 0 ? --props.steps : 0;

		if (props.steps > 0) {
			const height = (props.steps * props.hstep).toString() + "vh";
			this.elem.taOutput.style.height = height;
		} else {
			this.elem.taOutput.style.height = props.initHeight;
		}
	}
}


//export this view component as individual instances
//the view will get specified by the id and a CommandDef data object
const instances = new Map<string, CommandView>();

export function getView(args: FncArgs): CommandView {
	const id: string = args[0] as string;
	const def: CommandDef = args[1] as CommandDef;
	if (instances.has(id)) {
		return instances.get(id);
	} else {
		const view = new CommandView(id, def, args?.[2] as DynamicFunction);
		instances.set(id, view);
		return view;
	}
}

