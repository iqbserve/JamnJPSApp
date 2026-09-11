/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { findChildOf, setVisibility, setDisplay, typeUtil, fileUtil } from 'core/tools.mjs';
import { DataFile, ViewSource } from 'core/data-classes.mjs';
import { UIBuilder, onClicked, onDblClicked, DefaultCompProps, ContextId, newUIId, reworkHtmlElementIds } from 'core/uibuilder.mjs';
import type { UIComp } from 'core/uibuilder.mjs';
import * as Icons from 'core/icons.mjs';

import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';

/* Types */
import type { DialogMessage, JSObject, PropertiesObject } from 'types/commons';
import type { WorkbenchViewManager } from 'core/view-manager.mjs';
import type { ActionIcon } from 'core/uicomponents.mjs';

/**
 * Internal section
 */
const InternalUIBuilder = new UIBuilder()
	.setElementCollection(null as unknown as JSObject)
	.setObjectCollection(null as unknown as JSObject);

/**
 * Public section
 */

/**
 */
export function loadServerStyleSheet(path: string) {
	UIBuilder.loadServerStyleSheet(path);
}

/**
 * A basic view class. 
 */
export class AbstractView {
	//support for dynamic properties on this
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	[key: string]: any;

	//an automatic uid 
	uid = new ContextId();
	//a custom id
	id = "";
	viewSource = new ViewSource("");
	viewElement!: HTMLElement;
	//the obligatory flag to control the init sequence
	isInitialized = false;

	constructor(id: string, file: string | null = null) {
		this.id = id;
		this.viewSource = new ViewSource(file);
		this.isInitialized = false;
	}

	createViewElementFor(view: AbstractView, html: string | null) {
		if (html) {
			const template = document.createElement("template");
			template.innerHTML = html;
			view.viewElement = template.content.firstElementChild as HTMLElement;
			if (view instanceof AbstractView) {
				view.viewElement.id = view.id;
			}
		}
	}

	/**
	 * structural placeholder method
	 * default - viewSrc is expected to contain the view html
	 */
	getViewHtml(viewSrc: ViewSource, cb: (html: string | null) => void) {
		cb(viewSrc.getHtml());
	}

	/**
	 * Get and lazy create the view dom element.
	 */
	getViewElement(cb: (elem: HTMLElement) => void) {
		if (this.needsInitialization()) {
			this.getViewHtml(this.viewSource, (html) => {
				html = this.reworkHtml(html);
				this.beforeCreateViewElement();
				this.createViewElementFor(this, html);
				this.initialize();
				cb(this.viewElement);
			});
		} else {
			cb(this.viewElement);
		}
	}

	/**
	 */
	needsInitialization() {
		//a basic, overwriteable initialization logic
		return !this.isInitialized || !this.viewElement;
	}

	/**
	 */
	reworkHtml(html: string | null) {
		//to be overwritten
		return html;
	}

	/**
	 * has to be overwritten when working with the uid
	 */
	getElement(id: string) {
		return findChildOf(this.viewElement, id);
	}

	/**
	 */
	beforeCreateViewElement() {
		//to be overwritten
	}

	/**
	 */
	initialize() {
		//to be overwritten
	}

	setVisible(flag: boolean) {
		setVisibility(this.viewElement, flag);
	}

	setDisplay(elem: HTMLElement, flag: boolean | string) {
		setDisplay(elem, flag);
	}
}

/**
 * Work View base class.
 */
export class WorkView extends AbstractView {
	viewManager: WorkbenchViewManager | null = null;

	viewHeader!: WorkViewHeader;
	viewBody!: HTMLElement;
	viewWorkarea!: HTMLElement;
	sidePanel?: WorkViewSidepanel;

	bodyInitialDisplay!: string;

	state = {
		isRunning: false,
		isOpen: false,
		isPinned: false,
		isCollapsed: false
	}

	constructor(id: string, file: string | null) {
		super(id, file);

		this.state.isRunning = false;
		this.state.isOpen = false;
	}

	reworkHtml(html: string | null) {
		html = reworkHtmlElementIds(html ?? "", this.uid.get());
		return html;
	}

	//overwritten cause html id rework
	getElement(id: string) {
		return findChildOf(this.viewElement, this.uid.get(id));
	}

	initialize() {
		//to be overwritten
		//called from getViewElement(...)

		this.viewBody = this.getElement("work-view-body") as HTMLElement;
		this.viewWorkarea = this.getElement("work-view-workarea") as HTMLElement;
		this.bodyInitialDisplay = this.viewBody.style.display;

		const builder = new UIBuilder().setElementCollection(this);
		builder.newUICompFor(this.viewBody)
			.addDiv({ varid: "disableOverlay", clazzes: "work-view-disable-overlay" }, (divComp) => {
				divComp.addSpan((textComp) => {
					(divComp.domElem as JSObject)["setWorkingText"] = (text: string) => {
						textComp.html(text);
					}
				})
			});

		this.viewHeader = new WorkViewHeader(this, this.state);
		this.viewHeader.rightIconBar((bar) => {
			bar.addIcon({ id: "close.icon", title: "Close view" }, Icons.close(), () => {
				this.viewManager?.closeView(this);
			});
			bar.addIcon({ id: "pin.icon", title: "Pin to keep view" }, Icons.pin(), () => {
				this.togglePinned();
			});
			bar.addIcon({ id: "collapse.icon", title: "Collapse view" }, Icons.collapse(), () => {
				this.toggleCollapsed();
			});
		});

		this.viewHeader.menu((menu) => {
			if (this.viewManager) {
				menu
					.addItem("Close", () => {
						this.viewManager?.closeView(this);
					}, { separator: "bottom" })

					.addItem("Move up", () => {
						this.viewManager?.moveView(this, "up");
					})
					.addItem("Move down", () => {
						this.viewManager?.moveView(this, "down");
					})
					.addItem("Move to ...", () => {
						this.viewManager?.promptUserInput({ title: "", message: "Please enter your desired position number:", data: "1" },
							(value) => value ? this.viewManager?.moveView(this, value) : null
						);
					});
			}
		});
	}

	open() {
		this.viewHeader.menu().close();
		this.state.isOpen = true;
	}

	close() {
		if (this.isInitialized) {
			this.state.isOpen = false;
			this.viewHeader.menu().close();
		}
		return this.isCloseable();
	}

	isCloseable() {
		return !(this.state.isRunning || this.state.isPinned);
	}

	setRunning(flag: boolean) {
		this.state.isRunning = flag;
		this.viewHeader.showRunning(flag);
	}

	setDisabled(flag: boolean, options: PropertiesObject = {}) {
		options = { cursor: "wait", text: "Just a moment please ...", ...options }
		if (this.disableOverlay) {
			this.setDisplay(this.disableOverlay, flag ? "flex" : false);
			this.disableOverlay.style.cursor = flag ? options.cursor : "default";
			this.disableOverlay.setWorkingText(flag ? options.text : "");
		}
	}

	setTitle(title: string) {
		this.viewHeader.setTitle(title);
	}

	installSidePanel(sidePanelViewElem: HTMLElement | null, workareaElem = this.viewWorkarea): WorkViewSidepanel {
		this.sidePanel = new WorkViewSidepanel(this, workareaElem);
		if (sidePanelViewElem) {
			this.sidePanel.setViewComp(sidePanelViewElem);
		}

		this.viewHeader.rightIconBar((bar) => {
			bar.addIcon({ id: "sidepanel.icon", title: "Show/Hide Sidepanel" }, Icons.wkvSidePanel(), () => {
				this.toggleSidePanel();
			});
		});

		return this.sidePanel;
	}

	toggleSidePanel() {
		this.sidePanel?.toggle();
		this.viewHeader.icons["sidepanel.icon"].switch({
			flag: this.sidePanel?.isOpen(), cb: (icon, flag) => {
				icon.title = flag ? "Hide Sidepanel" : "Show Sidepanel";
			}
		});
	}

	togglePinned() {
		this.state.isPinned = !this.state.isPinned;
		this.viewHeader.icons["pin.icon"].switch({
			flag: this.state.isPinned, cb: (icon, flag) => {
				icon.title = flag ? "Unpin view" : "Pin to keep view";
				this.viewHeader.icons["close.icon"].setEnabled(!flag);
			}
		});

		return this.state.isPinned;
	}

	toggleCollapsed() {
		this.state.isCollapsed = !this.state.isCollapsed;
		this.viewHeader.container.classList.toggle("work-view-collapsed-header");

		this.viewHeader.icons["collapse.icon"].switch({
			flag: this.state.isCollapsed, cb: (icon, flag) => {
				const displayVal = flag ? "none" : this.bodyInitialDisplay;

				icon.title = flag ? "Expand view" : "Collapse  view";
				this.viewHeader.icons["sidepanel.icon"]?.setEnabled(!flag);
				setDisplay(this.viewBody, displayVal);
			}
		});

		return this.state.isCollapsed;
	}

	statusLineInfo(info: string) {
		WbApp.statusLineInfo(info);
	}

	copyToClipboard(text: string) {
		if (!this.state.isRunning && (text && text.length > 0)) {
			navigator.clipboard.writeText(text);
		}
	}

	saveToFile(fileName: string, text: string) {
		if (!this.state.isRunning && text.length > 0) {
			fileUtil.saveToFileClassic(fileName, text);
		}
	}

}

/**
 */
export class WorkViewHeader {
	view: WorkView;
	viewState: { isRunning: boolean, isOpen: boolean, isPinned: boolean, isCollapsed: boolean };

	container!: HTMLElement;
	icons: Record<string, ActionIcon> = {};
	headerMenu!: WorkViewHeaderMenu;
	iconBarLeft!: WorkViewHeaderIconBar;
	iconBarRight!: WorkViewHeaderIconBar;
	progressBar!: HTMLElement;
	title!: HTMLElement;

	constructor(view: WorkView, viewState: { isRunning: boolean, isOpen: boolean, isPinned: boolean, isCollapsed: boolean }) {
		this.view = view;
		this.viewState = viewState;
		this.#initialize();
	}

	#initialize() {
		this.container = this.#getElement("work-view-header");
		this.title = this.#getElement("view-title");
		this.headerMenu = new WorkViewHeaderMenu(this.#getElement("header-menu"));

		this.iconBarLeft = new WorkViewHeaderIconBar(this.#getElement("wkv-header-iconbar-left"), this.icons);
		this.iconBarLeft.addIcon({ id: "menu.icon", title: "View Menu" }, Icons.dotmenu(), (evt) => {
			this.#toggleHeaderMenu(evt);
		});
		this.iconBarRight = new WorkViewHeaderIconBar(this.#getElement("wkv-header-iconbar-right"), this.icons);
		this.progressBar = this.#getElement("wkv-header-progressbar");
	}

	#getElement(id: string): HTMLElement {
		return this.view.getElement(id) as HTMLElement;
	}

	#toggleHeaderMenu(evt: Event | null = null) {
		if (!this.viewState.isCollapsed) {
			this.headerMenu.toggleVisibility(evt);
		}
	}

	leftIconBar(configCb: ((bar: WorkViewHeaderIconBar) => void) | null = null) {
		if (configCb) {
			configCb(this.iconBarLeft);
		}
		return this.iconBarLeft;
	}

	rightIconBar(configCb: ((bar: WorkViewHeaderIconBar) => void) | null = null) {
		if (configCb) {
			configCb(this.iconBarRight);
		}
		return this.iconBarRight;
	}

	menu(configCb: ((menu: WorkViewHeaderMenu) => void) | null = null) {
		if (configCb) {
			configCb(this.headerMenu);
		}
		return this.headerMenu;
	}

	showRunning(flag?: boolean) {
		const classList = (this.progressBar.firstElementChild as HTMLElement).classList;
		const clazz = "progress-showWorking";
		classList.toggle(clazz);
		if (!this.viewState.isRunning && classList.contains(clazz)) {
			Logger.warn("isRunning flag mismatch");
		}
	}

	setTitle(text: string) {
		this.title.innerHTML = text;
	}
}

/**
 */
export class WorkViewSidepanel {
	view: WorkView;
	splitHandler?: SplitBarHandler;
	splitterElem!: HTMLElement;
	sidePanelElem!: HTMLElement;
	workareaElem: HTMLElement;
	viewCompElem?: HTMLElement;

	constructor(view: WorkView, workareaElem: HTMLElement) {
		this.view = view;
		this.workareaElem = workareaElem;
		this.#initialize();
	}

	#initialize() {

		this.splitterElem = this.view.getElement("work-view-sidepanel-splitter") as HTMLElement;
		this.sidePanelElem = this.view.getElement("work-view-sidepanel") as HTMLElement;

		if (this.splitterElem && this.sidePanelElem) {

			this.splitHandler = new SplitBarHandler(this.splitterElem)
				.setCompBefore(this.workareaElem)
				.setCompAfter(this.sidePanelElem)
				.build();

			this.setWidth("100px");
		}
	}

	#isClosed() {
		const val = this.splitterElem.style.display;
		return (!val || val == "none");
	}

	isOpen() {
		return !this.#isClosed();
	}

	toggle() {
		if (this.#isClosed()) {
			this.open();
		} else {
			this.close();
		}
		return this;
	}

	open() {
		setDisplay(this.splitterElem, true);
		setDisplay(this.sidePanelElem, true);
		return this;
	}

	close() {
		setDisplay(this.splitterElem, false);
		setDisplay(this.sidePanelElem, false);
		return this;
	}

	setViewComp(compElem: HTMLElement) {
		if (compElem) {
			if (this.viewCompElem) {
				this.viewCompElem.remove();
			}
			this.viewCompElem = compElem;
			this.sidePanelElem.append(this.viewCompElem);
		}
		return this;
	}

	setWidth(width: string) {
		this.sidePanelElem.style.width = width;
		return this;
	}
}

/**
 */
export class WorkViewHeaderMenu {

	#menuElem: HTMLElement;
	#toggleEvent: Event | null = null;

	constructor(containerElem: HTMLElement) {
		this.#menuElem = containerElem;

		window.addEventListener("click", (evt) => {
			this.#onAnyCloseTriggerEvent(evt)
		});
		window.addEventListener("mousedown", (evt) => {
			const elem = evt.target as HTMLElement;
			if (elem.classList.contains("splitter")) {
				this.#onAnyCloseTriggerEvent(evt)
			}
		}); // splitter
		window.addEventListener("scroll", (evt) => {
			this.#onAnyCloseTriggerEvent(evt)
		}, true); //true is necessary
	}

	#onAnyCloseTriggerEvent(evt: Event) {
		if (this.#toggleEvent !== evt) {
			this.close();
		}
	}

	#positionMenu(evt: Event) {
		const trigger = evt.currentTarget as HTMLElement;
		const rect = trigger.getBoundingClientRect();
		this.#menuElem.style.top = `${window.scrollY + rect.top - 10}px`;
		this.#menuElem.style.left = `${window.scrollX + rect.right + 10}px`;
	}

	toggleVisibility(evt: Event | null = null) {
		if (this.hasItems()) {
			this.#toggleEvent = evt;
			if (evt) { this.#positionMenu(evt); }
			setDisplay(this.#menuElem, this.#menuElem.style.display === "none");
		}
	}

	close() {
		setDisplay(this.#menuElem, false);
	}

	hasItems() {
		return this.#menuElem?.children.length > 0;
	}

	addItem(text: string, cb: (evt: Event) => void, props: PropertiesObject & { separator?: string, pos?: InsertPosition } = {}) {
		const item = document.createElement("a");
		item.href = "view: " + text;
		item.innerHTML = text;

		onClicked(item, (evt) => {
			//cause <a> links are used as menu items 
			//their default behavior must be suppressed
			evt.preventDefault();
			cb(evt);
		});

		if (props?.separator) {
			const clazz = props.separator === "top" ? "menu-separator-top" : "menu-separator-bottom";
			item.classList.add(clazz);
		}

		if (props?.pos) {
			this.#menuElem.insertAdjacentElement(props.pos, item);
		} else {
			this.#menuElem.appendChild(item);
		}
		return this;
	}
}

/**
 */
export class WorkViewHeaderIconBar {
	iconBarComp: UIComp;
	items: Record<string, HTMLElement>;

	constructor(iconBarElem: HTMLElement, items: Record<string, HTMLElement> = {}) {
		this.items = items;
		this.iconBarComp = InternalUIBuilder.newUICompFor(iconBarElem);
	}

	addIcon(props: { id: string, title: string }, icon: string, action: (evt: Event) => void) {
		this.iconBarComp.addActionIcon({ "iconName": icon, "title": props.title }, (icon) => {
			onClicked(icon, (evt) => { action(evt); });
			this.items[props.id] = icon.domElem;
		});
	}
}

/**
 * Common View Dialog 
 */
export class ViewDialog extends AbstractView {

	static default = { clazzes: [], attribProps: {}, styleProps: { "margin-top": "150px" } };

	parentElem!: HTMLElement;

	#dialogElem!: HTMLDialogElement;
	#dragHandler?: DialogDragHandler;
	#resizeHandler?: DialogResizeHandler;

	#lastPosition?: DOMRect;

	listener: Array<(dlg: ViewDialog) => void> = [];

	constructor() {
		super("");
	}

	initialize() {
		this.isInitialized = true;
	}

	getElement(id: string) {
		return findChildOf(this.dialog(), id);
	}

	createDialogElement(parent: HTMLElement | null = null) {
		if (parent) { this.parentElem = parent; }

		this.#dialogElem = document.createElement("dialog");
		this.#dialogElem.className = "view-dialog";
		this.#dialogElem.style.setProperty("margin-top", ViewDialog.default.styleProps["margin-top"]);
		this.parentElem.append(this.#dialogElem);
		this.initListener();
	}

	initListener() {
		this.#dialogElem.addEventListener('toggle', () => {
			this.listener.forEach((cb) => { cb(this) })
		});
	}

	/**
	 * the dialog standard layout
	 */
	createDefaultContentContainer() {
		const builder = new UIBuilder()
			//using this as target for ui builder var collecting
			//any "varid" gets a property of this
			.setUICompCollectionMode() //collect UIComps
			.setElementCollection(this)
			.setDefaultCompProps(new DefaultCompProps());

		builder.newUICompFor(this.dialog())
			.addDiv({ varid: "content", clazzes: "view-dialog-cartridge" }, (content) => {
				content
					.addDiv({ clazzes: "view-dialog-head-area" }, (headarea) => {
						headarea.addDiv({ varid: "header", clazzes: "view-dialog-header" }, (header) => {
							header
								.addSpan({ varid: "logoIcon", clazzes: ["dlg-header-item", "dlg-logo-icon"] })
								.addSpan({ varid: "title", clazzes: ["dlg-header-item", "dlg-title"] })
								.addRowContainer({ varid: "iconbar", clazzes: ["dlg-header-item", "dlg-header-iconbar"] }, (iconbar) => {
									iconbar.style({ display: "none" })
								})
								.addActionIcon({ varid: "closeIcon", iconName: Icons.close(), title: "Close" }, (closeIcon) => {
									closeIcon.class(["dlg-header-action-icon", "dlg-close-icon"]);
									onClicked(closeIcon, () => { this.close(); });
								})
						})
						headarea.addDiv({ varid: "progressBar", clazzes: "vdlg-header-progressbar" }, (progressbar) => {
							progressbar.addDiv({ clazzes: "header-progress-value" })
						});
					})
					.addDiv({ varid: "viewArea", clazzes: "view-dialog-content-area" })
					.addDiv({ varid: "commandArea" })
					.addDiv({ varid: "resizeThumb", clazzes: "view-dialog-resize-thumb" })
					.addDiv({ varid: "disableOverlay", clazzes: "view-dialog-disable-overlay" });
			});
	}

	initDragging() {
		this.#dragHandler = new DialogDragHandler(this, this.header.domElem);
		this.#dragHandler.setTriggerFilter((evt: Event) => {
			return (evt.target as HTMLElement).classList.contains("dlg-header-action-icon");
		});
		this.dialog().classList.add("draggable-dialog");
		this.#dragHandler.enabled = true;
	}

	initResizing() {
		this.#resizeHandler = new DialogResizeHandler(this.dialog(), this.resizeThumb.domElem);
		setDisplay(this.resizeThumb.domElem, true);
		this.#resizeHandler.enabled = true;
	}

	setTitle(title: string) {
		this.title.html(title);
		return this;
	}

	showRunning(flag: boolean | null = null) {
		const classList = (this.progressBar.domElem.firstElementChild as HTMLElement).classList;
		const clazz = "progress-showWorking";
		if (flag && !classList.contains(clazz)) {
			classList.add(clazz);
		} else if (!flag) {
			classList.remove(clazz);
		}
	}

	setDisabled(flag: boolean, cursor: string | null = null) {
		this.setDisplay(this.domElem.disableOverlay, flag);
		if (cursor) { this.disableOverlay.domElem.style.cursor = cursor; }
	}

	dialog(): HTMLDialogElement {
		return this.#dialogElem;
	}

	capturePosition() {
		this.#lastPosition = this.dialog().getBoundingClientRect();
	}

	positionDialog() {
		if (this.#lastPosition) {
			this.dialog().style.top = `${this.#lastPosition.top}px`;
			this.dialog().style.left = `${this.#lastPosition.left}px`;
		}
	}

	beforeOpen() {
		//to be overwritten
	}

	#open(cb: ((dlg: this) => void) | null, modal = false) {

		this.positionDialog();
		this.beforeOpen();
		if (cb) {
			cb(this);
		}
		if (modal) {
			this.dialog().showModal();
		} else {
			this.dialog().show();
		}
		if (this.#dragHandler) {
			if (!this.#lastPosition) {
				this.#dragHandler.setStartPosition(0, this.dialog().style.marginTop);
			}
			this.#dragHandler.startWorking();
		}
		if (this.#resizeHandler) {
			this.#resizeHandler.startWorking();
		}
	}

	isOpen() {
		return this.dialog().open;
	}

	open(cb: ((dlg: this) => void) | null) {
		this.#open(cb, false);
	}

	openModal(cb: ((dlg: this) => void) | null) {
		this.#open(cb, true);
	}

	close() {
		this.capturePosition();
		this.#dragHandler?.stop();
		this.#resizeHandler?.stop();
		this.dialog().close();
	}
}

/**
 * Standard dialog e.g. for confirmation/message/input
 */
export class StandardDialog extends ViewDialog {

	inputField!: HTMLInputElement;

	constructor(parent: HTMLElement) {
		super();
		this.parentElem = parent;
		this.initialize();
	}

	initialize() {
		this.createDialogElement();
		this.createDefaultContentContainer();

		this.dialog().classList.add("standard-dialog");

		this.header.class(["standard-dialog-header"]);
		this.title.class(["std-dlg-title"]);
		this.viewArea.class(["standard-dialog-content-area"])
		this.commandArea.class(["standard-dialog-command-area"])
			.addButton({ varid: "pbOk", text: "Ok", clazzes: "std-dlg-button" })
			.addButton({ varid: "pbCancel", text: "Cancel", clazzes: "std-dlg-button" }, (cancel: UIComp) => { cancel.domElem.autofocus = true; });

		this.initDragging();

		this.isInitialized = true;
	}

	#setupStandardActions<T>(cb: (value: T | null) => void, isInput = false) {
		onClicked(this.pbOk, () => {
			this.close();
			cb((isInput ? this.inputField.value : true) as T);
		});

		onClicked(this.pbCancel, () => {
			this.close();
			cb(null);
		});

		onClicked(this.closeIcon, () => {
			this.close();
			cb(null);
		});
	}

	capturePosition() {
		//do not save last position
	}

	openConfirmation(msg: DialogMessage, cb: (value: boolean) => void) {
		this.pbOk.html("Yes");
		this.pbCancel.html("No");
		this.#setupStandardActions<boolean>((value) => cb(value ?? false));

		this.setTitle(msg.title ? msg.title : "Confirmation required");
		this.viewArea.html(`<p>${msg.message}</p>`);

		this.openModal(null);
	}

	openInput(msg: DialogMessage, cb: (value: string) => void) {
		this.pbOk.html("Ok");
		this.pbCancel.html("Cancel");
		const inputId = "standard-dialog-input";

		const value = msg.data ? msg.data : "";

		this.#setupStandardActions<string>((value) => cb(value ?? ""), true);
		this.setTitle(msg.title ? msg.title : "Input");
		this.viewArea.html(`<p class="std-inputdlg-text">${msg.message}</p>
			<input type="text" id="${inputId}" class="std-dlg-textfield" value="${value}">`);

		this.inputField = findChildOf(this.dialog(), inputId) as HTMLInputElement;

		this.openModal(null);
		this.inputField.focus();
		this.inputField.select();
	}
}

/**
 */
export class WorkViewTableHandler {
	tableElem: HTMLTableElement;
	tableBody: HTMLTableSectionElement;
	tableData: TableData | null = null;
	ascOrder = false;
	sortIcon: ActionIcon;


	constructor(tableElem: HTMLTableElement) {
		this.tableElem = tableElem;
		this.tableBody = this.tableElem.querySelector('tbody') as HTMLTableSectionElement;

		this.sortIcon = this.getHeader(0).getElementsByTagName("a-icon")[0];
	}

	getHeader(idx: number) {
		return this.tableElem.getElementsByTagName("th")[idx];
	}

	setData(tableData: TableData) {
		this.clearData();
		this.tableData = tableData;

		this.tableData.rows.forEach((rowData, rowKey) => {
			const row = document.createElement("tr");
			row.className = "wkv";

			rowData.forEach((colVal, colKey) => {
				const col: HTMLTableCellElement = document.createElement("td");
				col.className = "wkv";
				col.innerHTML = colVal;
				(col as JSObject).value = colKey;
				onClicked(col, (evt) => { this.tableData?.cellClick(rowKey, colKey, evt); });
				onDblClicked(col, (evt) => { this.tableData?.cellDblClick(rowKey, colKey, evt); });
				row.appendChild(col);
			});

			this.tableBody.appendChild(row);
		});
	}

	clearData() {
		this.tableData = null;
		this.tableBody.replaceChildren();
	}

	sortByColumn(colIdx: number) {
		this.ascOrder = !this.ascOrder;
		const rows = Array.from(this.tableBody.querySelectorAll('tr'));

		rows.sort((rowA: HTMLElement, rowB: HTMLElement) => {
			const cellA = (rowA.querySelectorAll('td')[colIdx].textContent ?? "").trim();
			const cellB = (rowB.querySelectorAll('td')[colIdx].textContent ?? "").trim();

			return this.ascOrder ? cellA.localeCompare(cellB) : cellB.localeCompare(cellA);
		});

		this.tableBody.replaceChildren();
		this.tableBody.append(...rows);
	}

	toggleColSort() {
		this.sortIcon.switch();
	}

	filterRows(colIdx: number, filterText: string) {
		const rows = Array.from(this.tableBody.querySelectorAll('tr'));
		const filter = filterText.toLowerCase();

		rows.forEach((row: HTMLElement) => {
			const cellVal = row.querySelectorAll('td')[colIdx].textContent ?? "";
			row.style.display = cellVal.toLowerCase().includes(filter) ? "" : "none";
		});
	}

	newCellInputField(props: JSObject = {}) {
		const comp: HTMLSpanElement = document.createElement('span');
		const ctrl: HTMLInputElement = document.createElement('input');

		props = { clazz: "wkv-tblcell-edit-tf", booleanValue: null, datalist: [], ...props };

		(ctrl as JSObject).comp = comp;
		ctrl.type = "text";
		ctrl.classList.add(props.clazz ? props.clazz : "wkv-tblcell-edit-tf");
		comp.append(ctrl);

		if (props?.booleanValue != null) {
			ctrl.type = "checkbox";
			ctrl.checked = props.booleanValue;
			ctrl.style.width = "20px";
			onClicked(ctrl, () => { ctrl.value = typeUtil.stringFromBoolean(ctrl.checked) ?? "" });
		} else if (props.datalist?.length > 0) {
			let item = null;
			const dataElem = document.createElement("datalist");
			dataElem.id = newUIId();
			props.datalist.forEach((entry: string) => {
				item = document.createElement("option");
				item.value = entry;
				dataElem.append(item);
			});
			ctrl.setAttribute("list", dataElem.id);
			comp.append(dataElem);
		}
		return ctrl;
	}
}

/**
 * Table data represented as 
 * - map of rows (key:row)
 *  - each row a map of columns (key:column)
 */
export class TableData {
	rows: Map<string, Map<string, string>>;
	cellClick: (rowKey: string, colKey: string, evt: MouseEvent) => void;
	cellDblClick: (rowKey: string, colKey: string, evt: MouseEvent) => void;

	constructor() {
		this.rows = new Map();
		this.cellClick = () => { };
		this.cellDblClick = () => { };
	}

	addRow(key: string, columns: Map<string, string>) {
		this.rows.set(key, columns);
	}
}

/*********************************************
 * UI Handler
 *********************************************/
/**
 */
export class SplitBarHandler {

	static #dummyElem = document.createElement("span");
	static #dragOverlay = (() => {
		const overlay = document.createElement("div");
		overlay.className = "work-view-splitdrag-overlay";
		return overlay;
	})();

	dummyOffset = {
		width: 1000,
		height: 2000
	};

	splitter: HTMLElement;

	compBefore = SplitBarHandler.#dummyElem;
	compAfter = SplitBarHandler.#dummyElem;
	orientation = "v";
	moveSplitter = false;
	workClass = "splitter-working";
	typeClass = "vsplit";

	beforeSik: { pctWidth: string | null, pctHeight: string | null } = { pctWidth: null, pctHeight: null };
	afterSik: { pctWidth: string | null, pctHeight: string | null } = { pctWidth: null, pctHeight: null };
	hasPercentValues = false;

	isChanged = false;
	resizeListener: (() => void) | null = null;

	clickPoint!: { evt: MouseEvent, offsetLeft: number, offsetTop: number, beforeWidth: number, beforeHeight: number, afterHeight: number, afterWidth: number };

	barrierActionBefore: (handler: SplitBarHandler, value: number) => boolean = () => { return false; };
	barrierActionAfter: (handler: SplitBarHandler, value: number) => boolean = () => { return false; };

	constructor(splitter: HTMLElement) {
		this.splitter = splitter;
	}

	build() {
		this.typeClass = this.orientation + "split";

		this.beforeSik.pctWidth = this.compBefore.style.width.endsWith("%") ? this.compBefore.style.width : null;
		this.beforeSik.pctHeight = this.compBefore.style.height.endsWith("%") ? this.compBefore.style.height : null;
		this.afterSik.pctWidth = this.compAfter.style.width.endsWith("%") ? this.compAfter.style.width : null;
		this.afterSik.pctHeight = this.compAfter.style.height.endsWith("%") ? this.compAfter.style.height : null;

		this.hasPercentValues = !!(this.beforeSik.pctWidth || this.beforeSik.pctHeight || this.afterSik.pctWidth || this.afterSik.pctHeight);

		if (this.hasPercentValues) {
			this.resizeListener = () => {
				this.#adjustPercentValues();
			};
			window.addEventListener("resize", this.resizeListener);
		}

		this.splitter.onmousedown = (evt: MouseEvent) => {
			this.#onDragStart(evt);
		}
		return this;
	}

	setCompBefore(elem: HTMLElement) {
		this.compBefore = elem;
		return this;
	}

	setCompAfter(elem: HTMLElement) {
		this.compAfter = elem;
		return this;
	}

	setBarrierActionBefore(cb: (handler: SplitBarHandler, value: number) => boolean) {
		this.barrierActionBefore = cb;
		return this;
	}

	setBarrierActionAfter(cb: (handler: SplitBarHandler, value: number) => boolean) {
		this.barrierActionAfter = cb;
		return this;
	}

	setToHorizontalOrientation() {
		this.orientation = "h";
		this.typeClass = this.orientation + "split";
		return this;
	}

	#setOverlayActive(flag: boolean) {
		//avoid cursor flicker with overlay
		if (flag) {
			SplitBarHandler.#dragOverlay.classList.add(this.typeClass);
			document.body.appendChild(SplitBarHandler.#dragOverlay);
		} else {
			SplitBarHandler.#dragOverlay.remove();
			SplitBarHandler.#dragOverlay.classList.remove(this.typeClass);
		}
	}

	#adjustPercentValues() {
		if (this.isChanged) {
			if (this.beforeSik.pctWidth) {
				this.compBefore.style.width = this.beforeSik.pctWidth;
			}
			if (this.beforeSik.pctHeight) {
				this.compBefore.style.height = this.beforeSik.pctHeight;
			}
			if (this.afterSik.pctWidth) {
				this.compAfter.style.width = this.afterSik.pctWidth;
			}
			if (this.afterSik.pctHeight) {
				this.compAfter.style.height = this.afterSik.pctHeight;
			}
			this.isChanged = false;
		}
	}

	#onDragStart(evt: MouseEvent) {
		this.splitter.classList.toggle(this.workClass);

		this.#setOverlayActive(true);

		this.clickPoint = {
			evt,
			offsetLeft: this.splitter.offsetLeft,
			offsetTop: this.splitter.offsetTop,

			beforeWidth: this.compBefore.offsetWidth,
			beforeHeight: this.compBefore.offsetHeight,
			afterHeight: this.compAfter.offsetHeight,
			afterWidth: this.compAfter.offsetWidth
		};

		if (this.compBefore === SplitBarHandler.#dummyElem) {
			this.clickPoint.beforeWidth = this.dummyOffset.width;
			this.clickPoint.beforeHeight = this.dummyOffset.height;
		}
		if (this.compAfter === SplitBarHandler.#dummyElem) {
			this.clickPoint.afterWidth = this.dummyOffset.width;
			this.clickPoint.afterHeight = this.dummyOffset.height;
		}

		document.onmousemove = (evt) => {
			this.#doDrag(evt);
		};

		document.onmouseup = () => {
			document.onmousemove = document.onmouseup = null;
			this.#setOverlayActive(false);
			this.splitter.classList.toggle(this.workClass);
		}
	}

	#doDrag(evt: MouseEvent) {
		this.isChanged = true;
		const delta = {
			x: evt.clientX - this.clickPoint.evt.clientX,
			y: evt.clientY - this.clickPoint.evt.clientY
		};

		if (this.orientation === "v") {
			this.#doVDrag(delta);
		} else if (this.orientation === "h") {
			this.#doHDrag(delta);
		}
	}

	#doVDrag(delta: { x: number, y: number }) {
		delta.x = Math.min(Math.max(delta.x, -this.clickPoint.beforeWidth),
			this.clickPoint.afterWidth);

		const val = this.clickPoint.offsetLeft + delta.x;
		if (this.barrierActionBefore(this, val)) { return; }

		if (this.moveSplitter) {
			this.splitter.style.left = val + "px";
		}
		this.compBefore.style.width = (this.clickPoint.beforeWidth + delta.x) + "px";
		this.compAfter.style.width = (this.clickPoint.afterWidth - delta.x) + "px";
	}

	#doHDrag(delta: { x: number, y: number }) {
		delta.y = Math.min(Math.max(delta.y, -this.clickPoint.beforeHeight),
			this.clickPoint.afterHeight);

		const val = this.clickPoint.offsetTop + delta.y;
		if (this.barrierActionBefore(this, val)) { return; }

		if (this.moveSplitter) {
			this.splitter.style.top = val + "px";
		}
		this.compBefore.style.height = (this.clickPoint.beforeHeight + delta.y) + "px";
		this.compAfter.style.height = (this.clickPoint.afterHeight - delta.y) + "px";
	}

	stop() {
		document.dispatchEvent(new Event("mouseup", { bubbles: true, cancelable: true }));
	}
}

/**
 */
export class DialogDragHandler {
	#viewDialog?: ViewDialog;
	#dlg: HTMLElement;
	#handleElem: HTMLElement;
	#active = false;
	#start = { x: 0, y: 0 };
	#startPos = { left: 0, top: 0 };

	#triggerFilter: (evt: PointerEvent) => boolean = () => false;
	enabled = false;

	constructor(dialog: ViewDialog | HTMLElement, handleElem: HTMLElement) {
		if (dialog instanceof ViewDialog) {
			this.#viewDialog = dialog;
			this.#dlg = this.#viewDialog.dialog();
		} else {
			this.#dlg = dialog;
		}
		this.#handleElem = handleElem;
	}

	startWorking() {
		if (!this.enabled) { return this; }
		this.#handleElem.addEventListener('pointerdown', this.#onPointerDown);
		window.addEventListener('resize', this.#onWindowResize);
		return this;
	}

	stop() {
		if (!this.enabled) { return; }
		this.#stopDragging(null);
		this.#handleElem.removeEventListener('pointerdown', this.#onPointerDown);
		window.removeEventListener('resize', this.#onWindowResize);
	}

	#stopDragging(evt: PointerEvent | null) {
		if (!this.enabled) { return; }
		this.#active = false;
		if (evt) {
			this.#handleElem.releasePointerCapture(evt.pointerId);
		}
		document.removeEventListener('pointermove', this.#onPointerMove);
		document.removeEventListener('pointerup', this.#onPointerUp);
		document.removeEventListener('pointercancel', this.#onPointerUp);

		this.#viewDialog?.capturePosition();
	}

	setStartPosition(leftVal: number | string, topVal: number | string) {
		leftVal = typeUtil.isString(leftVal) ? Number.parseInt(leftVal as string, 10) : leftVal as number;
		topVal = typeUtil.isString(topVal) ? Number.parseInt(topVal as string, 10) : topVal as number;

		//center by default
		const left = leftVal == 0 ? Math.max(0, (window.innerWidth - this.#dlg.offsetWidth) / 2) : leftVal;
		const top = topVal == 0 ? Math.max(0, (window.innerHeight - this.#dlg.offsetHeight) / 2) : topVal;

		this.#dlg.style.left = Math.round(left) + 'px';
		this.#dlg.style.top = Math.round(top) + 'px';
		return this;
	}

	setTriggerFilter(cb: (evt: PointerEvent) => boolean) {
		this.#triggerFilter = cb;
		return this;
	}

	#onPointerDown = (evt: PointerEvent) => {
		if (this.#triggerFilter(evt)) return;
		this.#handleElem.setPointerCapture(evt.pointerId);
		this.#active = true;
		this.#start.x = evt.clientX;
		this.#start.y = evt.clientY;
		this.#startPos.left = Number.parseFloat(this.#dlg.style.left) || 0;
		this.#startPos.top = Number.parseFloat(this.#dlg.style.top) || 0;
		document.addEventListener('pointermove', this.#onPointerMove);
		document.addEventListener('pointerup', this.#onPointerUp);
		document.addEventListener('pointercancel', this.#onPointerUp);
	};

	#onPointerUp = (evt: PointerEvent) => {
		this.#stopDragging(evt);
	};

	#onPointerMove = (evt: PointerEvent) => {
		if (!this.#active) return;
		evt.preventDefault();
		const dx = evt.clientX - this.#start.x;
		const dy = evt.clientY - this.#start.y;
		let newLeft = this.#startPos.left + dx;
		let newTop = this.#startPos.top + dy;

		const maxLeft = window.innerWidth - this.#dlg.offsetWidth;
		const maxTop = window.innerHeight - this.#dlg.offsetHeight;
		newLeft = Math.min(Math.max(0, newLeft), maxLeft);
		newTop = Math.min(Math.max(0, newTop), maxTop);

		this.#dlg.style.left = Math.round(newLeft) + 'px';
		this.#dlg.style.top = Math.round(newTop) + 'px';
	};

	#onWindowResize = () => {
		const left = Number.parseFloat(this.#dlg.style.left) || 0;
		const top = Number.parseFloat(this.#dlg.style.top) || 0;
		const maxLeft = Math.max(0, window.innerWidth - this.#dlg.offsetWidth);
		const maxTop = Math.max(0, window.innerHeight - this.#dlg.offsetHeight);
		if (left > maxLeft) this.#dlg.style.left = Math.round(maxLeft) + 'px';
		if (top > maxTop) this.#dlg.style.top = Math.round(maxTop) + 'px';
	};
}

/**
 */
export class DialogResizeHandler {
	#dlg: HTMLElement;
	#handleElem: HTMLElement;

	enabled = false;

	startX = 0;
	startY = 0;
	startWidth = 0;
	startHeight = 0;
	startLeft = 0;
	startTop = 0;
	cfX = 0;
	cfY = 0;

	constructor(dialog: HTMLElement, handleElem: HTMLElement) {
		this.#dlg = dialog;
		this.#handleElem = handleElem;
	}

	startWorking() {
		if (!this.enabled) { return this; }
		this.#handleElem.addEventListener('pointerdown', this.#onPointerDown);
		return this;
	}

	stop() {
		if (!this.enabled) { return; }
		this.#handleElem.removeEventListener('pointerdown', this.#onPointerDown);
	}

	#stopResize = (evt: PointerEvent | null) => {
		if (!this.enabled) { return; }
		if (evt) {
			this.#handleElem.releasePointerCapture(evt.pointerId);
		}
		this.#handleElem.removeEventListener('pointermove', this.#onResize);
		this.#handleElem.removeEventListener('pointerup', this.#stopResize);
		this.#handleElem.removeEventListener('pointercancel', this.#stopResize);
	}

	#onPointerDown = (evt: PointerEvent) => {
		const rect = this.#dlg.getBoundingClientRect();

		this.startX = rect.right + this.cfX || 0;
		this.startY = rect.bottom + this.cfY || 0;
		this.startWidth = rect.width;
		this.startHeight = rect.height;
		this.startLeft = rect.left;
		this.startTop = rect.top;

		this.#dlg.style.position = 'fixed';
		this.#dlg.style.left = this.startLeft + 'px';
		this.#dlg.style.top = this.startTop + 'px';
		this.#dlg.style.margin = '0';

		this.#handleElem.setPointerCapture(evt.pointerId);
		this.#handleElem.addEventListener('pointermove', this.#onResize);
		this.#handleElem.addEventListener('pointerup', this.#stopResize);
		this.#handleElem.addEventListener('pointercancel', this.#stopResize);

	}

	#onResize = (evt: PointerEvent) => {
		const dx = evt.clientX - this.startX;
		const dy = evt.clientY - this.startY;

		this.#dlg.style.width = Math.max(0, this.startWidth + dx) + 'px';
		this.#dlg.style.height = Math.max(0, this.startHeight + dy) + 'px';
	}
}

/**
 */
export class AttachmentHandler {

	attachments = new Map<string, DataFile>();
	listElem: HTMLElement;

	constructor(listElem: HTMLElement) {
		this.listElem = listElem;
	}

	addData(dataFile: DataFile): void {
		if (dataFile && !this.attachments.has(dataFile.name)) {
			this.attachments.set(dataFile.name, dataFile);
			this.addDataToList(dataFile);
		}
	}

	addDataToList(dataFile: DataFile): void {
		const item = document.createElement("li");
		item.classList.add("indexed");
		const html = `<span class='${Icons.xRemove("class")} wkv-listitem-ctrl' title='Remove Attachment' style='margin-right: 20px;'></span> <span>${dataFile.name}</span>`;
		item.innerHTML = html;

		this.listElem.appendChild(item);

		onClicked(item.firstElementChild as HTMLElement, (evt) => {
			const name = (evt.target as HTMLElement).parentElement?.lastElementChild?.textContent ?? "";
			this.removeDataFromList(name, item);
		});
	}

	removeDataFromList(name: string, item: HTMLElement): void {
		item.remove();
		this.attachments.delete(name);
	}

	removeAllData(): void {
		this.attachments = new Map();
		this.listElem.innerHTML = "";
	}

	hasData(): boolean {
		return this.attachments.size > 0;
	}

	getData(): Map<string, DataFile> {
		return this.attachments;
	}

}