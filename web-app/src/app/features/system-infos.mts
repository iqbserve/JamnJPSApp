/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { typeUtil } from 'core/tools.mjs';
import { WorkView, WorkViewTableHandler, TableData } from 'core/view-classes.mjs';
import { UIBuilder, onClicked, onKeyup, KEY, UIComp } from 'core/uibuilder.mjs';
import { WorkViewHtml } from 'core/view-templates.mjs';
import * as Icons from 'core/icons.mjs';
import * as Webapi from 'app/core/webapi.mjs';

import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';

/**
 * A Sample Info/Config view
 * simulating table data processing
 */
class SystemInfoView extends WorkView {

	leftContainer: HTMLElement;
	appBoxElem = {
		tfName: null as HTMLInputElement,
		tfVersion: null as HTMLInputElement,
		tfDescription: null as HTMLInputElement,
		lnkReadMore: null as HTMLLinkElement,
	};

	configBoxElem = {
		icoSave: null as HTMLElement,
		icoRedo: null as HTMLElement,
	};
	configTable;

	needsViewDataRefresh = true;
	boxWidth: "720px";

	constructor(id: string) {
		super(id, null);
		this.viewSource.setHtml(WorkViewHtml());
	}

	initialize() {
		super.initialize();
		this.setTitle("System Infos");

		const builder = new UIBuilder()
			.setCompPropDefaults((props) => {
				props.get("label").styleProps = { "min-width": "80px", "text-align": "right" };
			});

		this.initWorkarea(builder);
		this.initAppBox(builder);
		this.initConfigBox(builder);

		this.isInitialized = true;
	}

	open() {
		super.open();

		getInfos((data) => {
			this.writeDataToView(data);
			this.setVisible(true);
		});
	}

	/**
	 */
	initWorkarea(builder) {

		builder.setElementCollection(this);
		builder.newUICompFor(this.viewWorkarea)
			.style({ "display": "flex", "flex-direction": "row" })
			.addColContainer({ varid: "leftContainer" })
			.addColContainer({ varid: "rightContainer" }, (comp) => {
				comp.style({ width: "100%", "justify-content": "center", "align-items": "center", "margin-left": "40px", "margin-right": "20px" })
					.add("img", (image) => {
						image.attrib({ src: "assets/intro.png", alt: "App Info", title: "Jamn Workbench" })
							.style({ width: "350px", border: "1px solid var(--border-gray)" });
					});
			});
	}

	/**
	 */
	initAppBox(builder) {

		let compSet;
		builder.setElementCollection(this.appBoxElem);

		builder.newUICompFor(this.leftContainer)
			.addFieldset({ title: "Application" }, (fieldset) => {
				fieldset.style({ width: this.boxWidth });
				compSet = fieldset.getDomElem();
			});

		builder.newUIComp()
			.addLabelTextField({ text: "Name:" }, { varid: "tfName", readOnly: true }, (label, textField) => {
				textField.style({ "font-size": "18px", color: "var(--isa-title-grayblue)" });
			})
			.appendTo(compSet);

		builder.newUIComp()
			.addLabelTextField({ text: "Version:" }, { varid: "tfVersion", readOnly: true })
			.appendTo(compSet);

		builder.newUIComp()
			.style({ "align-items": "baseline", "padding-right": "5px", "margin-top": "20px" })
			.addLabelTextArea({ text: "Description:", name: "lbDescr" }, { varid: "tfDescription", rows: 3, readOnly: true })
			.appendTo(compSet);

		builder.newUIComp()
			.style({ "flex-direction": "row-reverse" })
			.addLink({ varid: "lnkReadMore", text: "Read more on GitHub ... " }, (link) => {
				link.style({ "text-align": "right" })
					.attrib({ title: "Jamn Personal Server - All-In-One MicroService App", target: "GitHub_Repos" })
			})
			.appendTo(compSet);
	}

	/**
	 */
	initConfigBox(builder) {

		let fieldset: UIComp;
		builder.setElementCollection(this.configBoxElem);

		builder.newUICompFor(this.leftContainer)
			.addFieldset({ title: "Configuration" }, (comp) => {
				comp.style({ "padding-top": "10px", width: this.boxWidth });
				fieldset = comp;
			});

		fieldset.addRowContainer((comp) => {
			comp.style({ "flex-direction": "row-reverse", "margin-bottom": "10px", "gap": "15px" })
				.addActionIcon({ varid: "icoSave", iconName: Icons.save(), title: "Save current changes" }, (saveIcon) => {
					onClicked(saveIcon, () => {
						updateInfos(getUpdateRequest(), (response) => {
							if (response?.status === "ok") {
								clearConfigChanges()
								Logger.info("App-Info update done");
							}
						});
					});
				})
				.addActionIcon({ varid: "icoRedo", iconName: Icons.redo(), title: "Undo changes" }, (redoIcon) => {
					onClicked(redoIcon, () => {
						//open confirmation dialog
						WbApp.confirm({
							message: "<b>Undo all changes</b><br>Do you want to discard all changes?"
						}, (value) => value ? clearConfigChanges(true) : null);
					});
				});
		});

		fieldset.addFromHtml(this.reworkHtml(tableHtml), (elems: Element[]) => {
			const tableElem = elems[0].firstElementChild;
			this.configTable = new WorkViewTableHandler(tableElem);
		});

		this.setActionsEnabled(false);
	}

	/**
	 */
	setActionsEnabled(flag) {
		const ctrls = [this.configBoxElem.icoSave, this.configBoxElem.icoRedo];
		const styleProps = flag ? { "pointer-events": "all", color: "" } : { "pointer-events": "none", color: "var(--border-gray)" };

		ctrls.forEach((ctrl) => UIBuilder.setStyleOf(ctrl, styleProps));
	}

	/**
	 */
	writeDataToView(data) {
		if (this.needsViewDataRefresh) {
			clearConfigChanges();

			const sysProps = data.sysProps;
			const buildProps = data.buildProps;

			this.appBoxElem.tfName.value = buildProps["appname"];
			this.appBoxElem.tfVersion.value = `${buildProps["version"]} - Build [${buildProps["build.date"]} UTC]`;
			this.appBoxElem.tfDescription.value = buildProps["description"];
			this.appBoxElem.lnkReadMore.href = buildProps["readme.url"];

			//create+build a table data object
			const tableData = new TableData();
			// "data.config" has the structure: { name1:value1, name2:value2 ... }
			// create a 2 column tableData from it
			const names = Object.getOwnPropertyNames(sysProps);
			names.forEach((name) => {
				const row = new Map();
				//mark the read only key column to filter out 
				row.set("key:" + name, name);
				row.set(name, sysProps[name]);
				tableData.addRow(name, row);
			})

			//define cell editing on double click
			tableData.cellDblClick = (rowKey: string, colKey: string, evt: MouseEvent) => {

				//editing only for the value column
				if (!colKey.startsWith("key:")) {
					//get the origin data from the data object (model)
					const dataRow = tableData.rows.get(rowKey);
					const dataValue = dataRow.get(colKey);

					//create+handle a simple cell input field
					const cellElem = evt.currentTarget as HTMLElement;
					if (cellElem.getElementsByTagName('input').length > 0) return;
					//for simplicity use the html table cell value
					const orgCellValue = cellElem.innerHTML;
					cellElem.innerHTML = '';

					const inputFieldProps = { booleanValue: typeUtil.booleanFromString(orgCellValue) };
					const cellInput = this.configTable.newCellInputField(inputFieldProps);
					cellInput.value = orgCellValue;

					cellInput.onblur = () => {
						let newValue = cellInput.value;
						cellInput.comp.remove();
						if (typeUtil.isBooleanString(newValue) && !typeUtil.isBooleanString(orgCellValue)) {
							newValue = orgCellValue;
						}
						cellElem.innerHTML = newValue === orgCellValue ? orgCellValue : newValue;
						ckeckConfigChange(colKey, dataValue, cellElem);
					};

					cellInput.onkeydown = (evt) => {
						if (KEY.isEnter(evt)) {
							cellInput.blur();
						} else if (KEY.isEscape(evt)) {
							cellInput.blur();
							cellElem.innerHTML = orgCellValue;
							ckeckConfigChange(colKey, dataValue, cellElem);
						}
					};

					cellElem.appendChild(cellInput.comp);
					cellInput.focus();
				}
			};

			this.configTable.setData(tableData);
			this.configTable.sortByColumn(0);

			onClicked(this.configTable.getHeader(0).getElementsByTagName("a-icon")[0], () => {
				this.configTable.sortByColumn(0);
				this.configTable.toggleColSort(0);
			});

			onKeyup(this.configTable.getHeader(0).getElementsByTagName("input")[0], (evt) => {
				this.configTable.filterRows(0, evt.target.value);
			});
			this.needsViewDataRefresh = false;
		}
	}
}

//export this view component as singleton instance
let viewInstance: SystemInfoView;
export function getView() {
	if (!viewInstance) {
		viewInstance = new SystemInfoView("systemInfoView");
	}
	return viewInstance;
}

const configChanges = new Map();
let systemConfigData = null;

/**
 */
function getInfos(cb) {
	if (systemConfigData) {
		cb(systemConfigData);
	} else {
		Webapi.doGET(`${Webapi.service_get_wbappconfiguration}?name=system`, { parseJson: true }).then((data) => {
			systemConfigData = data;
			cb(systemConfigData);
		});
	}
}

/**
 */
function updateInfos(request, cb) {
	//do nothing in demo sample
	//let jsonRquest = JSON.stringify(request);
	const response = { status: "ok" };
	cb(response);
}

/**
 */
function clearConfigChanges(undo = false) {
	configChanges.forEach((cell) => {
		cell.elem.style["border-left"] = "";
		if (undo) { cell.elem.innerHTML = cell.orgData; };
	});
	configChanges.clear();
	getView().setActionsEnabled(false);
}

/**
 */
function ckeckConfigChange(key, orgVal, cellElem) {
	const currentVal = cellElem.innerHTML;

	if (orgVal === currentVal) {
		configChanges.delete(key);
		cellElem.style["border-left"] = "";
	} else {
		configChanges.set(key, { elem: cellElem, orgData: orgVal });
		cellElem.style["border-left"] = "3px solid #32cd32";
	}
	getView().setActionsEnabled(configChanges.size !== 0);
}

/**
 */
function getUpdateRequest() {
	const request = { "configChanges": {} };
	configChanges.forEach((cell, key) => {
		request.configChanges[key] = cell.elem.innerHTML;
	});
	return request;
}


const tableHtml = `
<div class="wkv-fix-tblhead-container">
	<table class="wkv" style="table-layout:fixed;">
		<thead>
			<tr>
				<th class="wkv" style="width: 250px;">
					<span style="display: flex; align-items: center;">
						<span>Key:</span>
						<input type="text" id="config.filter.tf" placeholder="Filter ..."
							class="embedded-search-field" style="min-width: 60%;">
						<span style="width: 100%;"></span>
						<a-icon iconname="${Icons.tableSort()}" class="wkv-tblheader-ctrl" title="Sort"></a-icon>
					</span>
				</th>
				<th class="wkv">Value:</th>
			</tr>
		</thead>
		<tbody></tbody>
	</table>
</div>
`