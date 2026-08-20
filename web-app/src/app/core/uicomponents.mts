/* Authored by iqbserve.de */

import { UIBuilder, onClicked, onKeyup, DataList, onInput, UIComp, UIBuildConfig, UIElemProps } from 'core/uibuilder.mjs';
import * as Icons from 'core/icons.mjs';

/* Types */
import type { ConfigObject, JSObject } from 'types/commons';

/**
 * App titelbar component
 */
export class WbTitlebar extends HTMLElement {
    static TagName = "wb-titlebar";

    #elem: JSObject = {};

    constructor() {
        super();
    }

    #createUI(builder: UIBuilder) {
        builder.setElementCollection(this.#elem);

        builder.newUICompFor(this)
            .style({ "user-select": "none" })
            .add("a", (logoIcon) => {
                logoIcon.attrib({ href: "https://iqbserve.de/", target: "iqbserve.de" }).style({ "min-width": "fit-content" })
                    .add("img", (logoIconImg) => {
                        logoIconImg.class("wtb-item")
                            .attrib({ src: "assets/workbench-logo.png", title: "IQB Services", alt: "logo" })
                            .style({ width: "22px", height: "22px" });
                    })
            })
            .addContainer((elem) => {
                elem.html("Jamn Workbench -").class("wtb-item").style({ "min-width": "fit-content" });
            })
            .addContainer({ varid: "titleText" }, (elem) => {
                elem.html("[ ]").class("wtb-item").style({ width: "100%", "user-select": "text" });
            })
            .addContainer((titleIconBar) => {
                titleIconBar.class(["wtb-item", "wtb-ctrl-panel"])
                    .addActionIcon({ varid: "icoStepup", iconName: Icons.caretup() })
                    .addActionIcon({ varid: "icoStepdown", iconName: Icons.caretdown() });
            });
    }

    build(builder: UIBuilder) {
        this.#createUI(builder);
        return this;
    }

    setStepViewsDownAction(cb: () => void, title?: string) {
        if (title) { this.#elem.icoStepdown.title = title; }
        onClicked(this.#elem.icoStepdown, () => { cb(); });
        return this;
    }

    setStepViewsUpAction(cb: () => void, title?: string) {
        if (title) { this.#elem.icoStepup.title = title; }
        onClicked(this.#elem.icoStepup, () => { cb(); });
        return this;
    }

    setTitleText(text: string) {
        this.#elem.titleText.innerHTML = `[ ${text} ]`;
    }

}

/**
 * App statusline component
 */
export class WbStatusline extends HTMLElement {
    static TagName = "wb-statusline";

    #elem: JSObject = {};

    constructor() {
        super();
    }

    #createUI(builder: UIBuilder) {
        builder.setElementCollection(this.#elem);

        builder.newUICompFor(this)
            .style({ "user-select": "none" })
            .addContainer((prefixText) => {
                prefixText.html("Info:").class("wsl-item").style({ width: "30px" });
            })
            .addContainer({ varid: "infoText" }, (infoText) => {
                infoText.class("wsl-item").style({ width: "100%", "user-select": "text" });
            })
            .addContainer((iconBar) => {
                iconBar.class("wsl-item").style({ width: "50px", "margin-right": "5px", "text-align": "center" })
                    .add({ elemType: "a", varid: "gitLink" }, (gitLink) => {
                        gitLink.class(Icons.github("classes"))
                            .attrib({ title: "Git repo", target: "GitHub_Repos" });
                    });
            });
    }

    build(builder: UIBuilder) {
        this.#createUI(builder);
        return this;
    }

    setScmUrl(url: string) {
        this.#elem.gitLink.href = url;
        return this;
    }

    setInfoText(text: string) {
        this.#elem.infoText.innerHTML = text;
    }
}

/**
 * App sidebar component
 */
export class WbSidebar extends HTMLElement {
    static TagName = "wb-sidebar";

    #topicDefs: Array<ConfigObject> = [];
    #workItemDefs: Array<ConfigObject> = [];
    #itemAction: (featureName: string) => void = () => { };

    #elem: JSObject = {};

    constructor() {
        super();
    }

    #createUI(builder: UIBuilder) {
        builder.setElementCollection(this.#elem);

        builder.newUICompFor(this)
            .addContainer({ varid: "header", clazzes: "sidebar-header" }, (header) => {
                header.addActionIcon({ varid: "menuIcon", iconName: Icons.menu() }, (menuIcon) => {
                    menuIcon.title("Show/Hide sidebar menu").class("sidebar-header-icon");
                    onClicked(menuIcon, () => {
                        this.toggleCollapse();
                    })
                })
                    .addContainer({ varid: "workIconBar", clazzes: "sidebar-header-workicons" });
            })
            .addContainer({ varid: "body", clazzes: "sidebar-body" }, (body) => {
                body.addContainer({ varid: "topicHead", clazzes: "sbar-topic-head" }, (topicHead) => {
                    topicHead.addActionIcon({ iconName: Icons.gi_toggleExpand() }, (icon) => {
                        icon.title("Expand/Collapse Topics").class("sidebar-header-icon").style({ "font-size": "14px", "margin-left": "10px" });
                        onClicked(icon, () => {
                            this.#expandTopics(icon.domElem);
                        });
                    })
                        .addTextField({ clazzes: "embedded-search-field" }, (searchField) => {
                            searchField.style({ width: "50%", "max-width": "150px" }).attrib({ placeholder: "Filter ..." });
                            onKeyup(searchField, (evt) => {
                                this.#filterItems(evt.target.value);
                            })
                        });
                })
            })
            .addList({ varid: "topicList", clazzes: "sbar-topic-list" });
    }

    #newWorkIcon(id, iconName) {
        const elem = UIBuilder.createDomElementFrom(`<a-icon iconname="${iconName}" id="${id}" class="sidebar-header-icon"></a-icon>`);
        return elem;
    }

    #onItemClick(evt: Event) {
        const target = evt.target as HTMLElement;
        this.#itemAction(target.dataset.feature);
    }

    #createTopicList() {

        const topicListElem = this.#elem.topicList;
        let topicElem = null;
        let itemElem = null;
        let itemListElem = null;

        this.#topicDefs.forEach((topicDef: ConfigObject) => {
            topicElem = this.#newTopic(topicDef.id, topicDef);
            itemListElem = this.#newTopicList();
            topicElem.append(itemListElem);
            topicListElem.append(topicElem);

            (topicDef.items as Array<ConfigObject>)?.forEach((itemDef: ConfigObject) => {
                itemElem = this.#newTopicItem(itemDef.id, itemDef.text, itemDef.feature);
                itemListElem.append(itemElem);
                onClicked(itemElem, (evt) => {
                    this.#onItemClick(evt);
                });
            });
        });
    }

    #newTopic(key, def) {

        const iconClass = Icons.getIconClassString(def.icon);
        const text = def.text;
        const html = `
        <li class="sbar-topic" name="${text}">
            <span class="sbar-topic-header node-trigger">
                <span class="sbar-topic-icon ${iconClass} node-trigger"></span>
                <span class="sbar-topic-text node-trigger">${text}</span>
            </span>
        </li>`;
        const elem = UIBuilder.createDomElementFrom(html);

        onClicked(elem, (evt) => {
            //prevent collapsing topic
            if (evt.target.classList.contains("node-trigger")) {
                const list = evt.currentTarget.lastChild;
                if (list) {
                    if (list.style.display == "none" || list.style.display == "") {
                        list.style.display = "block";
                    } else {
                        list.style.display = "none";
                    }
                }
            }
        });

        return elem;
    }

    #newTopicList() {
        const html = `<ul class="sbar-item-list"></ul>`;
        const list = UIBuilder.createDomElementFrom(html);
        return list;
    }

    #newTopicItem(id, text, feature) {
        id = id ? "id=" + id : "";
        feature = feature ? `data-feature="${feature}"` : "";
        const html = `<li class="sbar-item" ${id} ${feature}>${text}</li>`;
        return UIBuilder.createDomElementFrom(html);
    }

    #expandTopics(icon) {
        let displayVal;
        icon.switch({
            cb: (icon, flag) => {
                icon.title = flag ? "Collapse Topics" : "Expand Topics";
                displayVal = flag ? "block" : "none";
            }
        });

        const topics = this.querySelectorAll<HTMLElement>("ul.sbar-item-list")
        for (const list of topics) {
            list.style.display = displayVal;
        }
    }

    #filterItems(text = "") {
        const filter = text.trim().toLowerCase();
        const items = this.querySelectorAll<HTMLElement>("li.sbar-item")
        const hasFilter = filter.length > 0;

        if (hasFilter) {
            this.#elem.topicHead.classList.add("topic-head-freez");
        } else {
            this.#elem.topicHead.classList.remove("topic-head-freez");
        }

        const showItem = (flag, item) => {
            const topic = item.parentElement.parentElement;

            item.style.display = flag ? "" : "none";
            if (flag) {
                //ensure items are visible - not collapsed
                item.parentElement.style.display = "block"
            };
            if (item.parentElement.querySelectorAll('li:not([style*="display: none;"])').length == 0) {
                topic.style.display = "none";
            } else {
                topic.style.display = "block";
            }
        };

        let itemText: string;
        for (const item of items) {
            if (hasFilter) {
                itemText = item.innerText.trim().toLowerCase();
                if (itemText.includes(filter)) {
                    showItem(true, item);
                } else {
                    showItem(false, item);
                }
            } else {
                showItem(true, item);
            }
        }
    }

    #createWorkItemPanel() {
        this.#workItemDefs.forEach(def => this.addHeaderWorkIcon(def));
    }

    setItemAction(cb: (featureName: string) => void) {
        this.#itemAction = cb;
        return this;
    }

    setTopicDefs(topicDefs: Array<ConfigObject>) {
        this.#topicDefs = topicDefs;
        return this;
    }

    setWorkItemDefs(workItemDefs: Array<ConfigObject>) {
        this.#workItemDefs = workItemDefs;
        return this;
    }

    build(builder: UIBuilder) {
        this.#createUI(builder);
        this.#createTopicList();
        this.#createWorkItemPanel();
        return this;
    }

    isCollapsed() {
        return this.#elem.menuIcon.classList.contains("rot90");
    }

    toggleCollapse() {
        if (this.isCollapsed()) {
            this.#elem.topicList.style.display = "block";
            this.#elem.body.style.display = "block";
            this.#elem.menuIcon.classList.toggle("rot90");
            this.style.width = "225px";
        } else {
            this.#elem.topicList.style.display = "none";
            this.#elem.body.style.display = "none";
            this.#elem.menuIcon.classList.toggle("rot90");
            this.style.width = "50px";
        }
    }

    /**
     * { text: , id: , icon:  }
     */
    addHeaderWorkIcon(def) {
        let icon = null;
        const workIconBar = this.#elem.workIconBar;
        if (def.icon) {
            icon = this.#newWorkIcon(def.id, def.icon);
            icon.title = def.text ? def.text : "";
            icon.dataset.feature = def.feature ? def.feature : "";
            workIconBar.append(icon);
            if (icon.dataset.feature) {
                workIconBar[icon.dataset.feature] = icon;
            }

            onClicked(icon, (evt) => {
                this.#onItemClick(evt);
            });
        }
        return icon;
    }

    getItem(featureId: string): HTMLElement | null {
        return this.#elem.topicList.querySelector(`li[data-feature="${featureId.trim()}"]`);
    }

    getWorkPanelIcon(featureId: string): ActionIcon | null {
        return this.#elem.workIconBar[featureId];
    }
}

/**
 * Action icon component
 */
export class ActionIcon extends HTMLElement {
    static TagName = "a-icon";

    static observedAttributes = ['iconname'];

    iconname = "";
    constructor() {
        super();
    }

    #getIconClasses(): string[] {
        return Icons.getIconClasses(this.iconname);
    }

    #getShapeClass(idx = 0): string {
        return Icons.getIconShapeClasses(this.iconname)[idx];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (name === "iconname") {
            this.iconname = newValue;
            this.#getIconClasses().forEach(clazz => {
                this.classList.add(clazz);
            });
        }
    }

    hasInitialShape(): boolean {
        return this.classList.contains(this.#getShapeClass(0));
    }

    setEnabled(flag: boolean) {
        if (flag) {
            this.style["pointer-events"] = "all";
        } else {
            this.style["pointer-events"] = "none";
        }
    }

    switch(options?: { flag?: boolean, cb?: (icon: ActionIcon, flag: boolean) => void }): void {
        const opts = { flag: this.hasInitialShape(), cb: null, ...options };

        if (opts.flag) {
            this.classList.remove(this.#getShapeClass(0));
            this.classList.add(this.#getShapeClass(1));
        } else {
            this.classList.add(this.#getShapeClass(0));
            this.classList.remove(this.#getShapeClass(1));
        }
        if (opts.cb) {
            opts.cb(this, opts.flag);
        }
    }
}

/**
 * Simple CRUD component
 */
export class SimpleCrudComp extends HTMLElement {
    static TagName = "simple-crud-comp";

    static ICON_ERASE = "iconErase";
    static ICON_SAVE = "iconSave";
    static ICON_DELETE = "iconDelete";
    static SEPARATOR = "separator";

    elem: JSObject = {};
    dataList: DataList;

    constructor() {
        super();
    }

    createUI(builder: UIBuilder) {
        const comp = builder.newUICompFor(this);

        comp.style({ display: "flex", "gap": comp.domElem.style.gap || "10px" })
            .addRowContainer(null, (fieldPart) => {
                fieldPart
                    .style({ "align-items": "center" })
                    .addLabelTextField(
                        { varid: "lbData", text: "Name:" }, { varid: "tfData", datalist: [] }, (label, field) => {
                            field.style({ width: "250px" });
                        })
            });

        this.createActionbar(comp);
        this.dataList = builder.getDataListFor("tfData");

        return comp;
    }

    createActionbar(comp: UIComp) {
        comp.addRowContainer({ varid: "actionBar" }, (actionBar) => {
            actionBar
                .style({ "align-items": "center", "gap": "20px" })
                .addActionIcon({ varid: "iconErase", iconName: Icons.eraser(), title: "Clear current selection" }, (icon) => {
                    icon.style({ "order": "1" });
                })
                .addActionIcon({ varid: "iconSave", iconName: Icons.save(), title: "Save current selection" }, (icon) => {
                    icon.style({ "order": "2" });
                })
                .add({ varid: "separator", elemType: "span" }, (separator) => {
                    separator.style({ "order": "3", height: "20px", "border-right": "1px solid var(--border-gray)" })
                })
                .addActionIcon({ varid: "iconDelete", iconName: Icons.trash(), title: "Delete current selection" }, (icon) => {
                    icon.style({ "order": "4" });
                });
        });
    }

    build(builder: UIBuilder, config?: UIBuildConfig) {
        const ctx = { builder: builder, options: config?.options };
        if (config?.beforeBuild) {
            config.beforeBuild(this, ctx);
        } else {
            this.beforeBuild(ctx);
        }

        const comp = this.createUI(ctx.builder);
        if (config?.afterBuild) {
            config.afterBuild(comp);
        }
        return this;
    }

    beforeBuild(ctx: { builder: UIBuilder }) {
        //create a new builder to keep elem collection internal
        ctx.builder = new UIBuilder(ctx.builder.getDefaultCompProps()).setElementCollection(this.elem);
    }

    setStyle(styleProps: UIElemProps) {
        UIBuilder.setStyleOf(this, styleProps);
        return this;
    }

    configure(cb: (comp: SimpleCrudComp, label: HTMLLabelElement, field: HTMLInputElement, actionBar: HTMLElement) => void) {
        cb(this, this.elem.lbData, this.elem.tfData, this.elem.actionBar);
        return this;
    }

    getFieldValue() {
        return this.elem.tfData.value;
    }

    setFieldValue(value: string) {
        this.elem.tfData.value = value;
    }

    hasFieldValue(): boolean {
        return this.elem.tfData.value.trim() !== "";
    }

    appendTo(target: HTMLElement) {
        target.append(this);
        return this;
    }

    setLabelText(text: string) {
        this.elem.lbData.textContent = text;
        return this;
    }

    displayLabel(flag: boolean) {
        this.elem.lbData.style.display = flag ? "" : "none";
        return this;
    }

    displaySeparator(flag: boolean) {
        this.elem.separator.style.display = flag ? "" : "none";
        return this;
    }

    setPlaceholder(text: string) {
        this.elem.tfData.setAttribute("placeholder", text);
        return this;
    }

    setIconPos(name: string, pos: number) {
        this.elem[name].style.order = pos;
        return this;
    }

    setBarPosition(pos: 'right' | 'top' | 'bottom') {
        if (pos == 'right') {
            this.style["flex-direction"] = "row";
        } else if (pos == 'top') {
            this.style["flex-direction"] = "column-reverse";
        } else if (pos == 'bottom') {
            this.style["flex-direction"] = "column";
        }
        return this;
    }

    setFieldWidth(width: string) {
        this.elem.tfData.style["width"] = width;
        return this;
    }

    setItems(items: string[]) {
        this.dataList.setOptions(items)
        return this;
    }

    addItem(item: string) {
        this.dataList.addOption(item);
        return this;
    }

    removeItem(item: string) {
        this.dataList.removeOption(item);
        return this;
    }

    setSelectionChangedAction(cb: (comp: SimpleCrudComp, evt?: Event) => void) {
        onInput(this.elem.tfData, (evt: Event) => {
            cb(this, evt);
        });
        return this;
    }

    setClearAction(cb: (comp: SimpleCrudComp, evt?: Event) => void, title?: string) {
        if (title) { this.elem.iconErase.title = title; }
        onClicked(this.elem.iconErase, (evt: Event) => {
            cb(this, evt);
        });
        return this;
    }

    setSaveAction(cb: () => void, title?: string) {
        if (title) { this.elem.iconSave.title = title; }
        onClicked(this.elem.iconSave, cb);
        return this;
    }

    setDeleteAction(cb: () => void, title?: string) {
        if (title) { this.elem.iconDelete.title = title; }
        onClicked(this.elem.iconDelete, cb);
        return this;
    }
}


export function registerUIWebComponents() {
    customElements.define(WbTitlebar.TagName, WbTitlebar);
    customElements.define(WbSidebar.TagName, WbSidebar);
    customElements.define(WbStatusline.TagName, WbStatusline);
    customElements.define(ActionIcon.TagName, ActionIcon);
    customElements.define(SimpleCrudComp.TagName, SimpleCrudComp);
}
