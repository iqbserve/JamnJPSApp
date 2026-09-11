/* Authored by iqbserve.de */

import { typeUtil, mergeArrayInto } from 'core/tools.mjs';
import * as Icons from 'core/icons.mjs';
import { JSObject } from 'types/commons';

/* Types */
/**
 * <pre>
 * The loose "DSL bag" passed to builder methods.
 * Known fields are typed for autocompletion/checking, but the DSL intentionally
 * stays open (index signature) since callers may pass any HTML-attribute-like key.
 * </pre>
 */
export interface UICompDef {
    elemType?: string;
    typeId?: string;
    varid?: string;
    clazzes?: string | string[];
    pos?: "top" | number;
    nodefaults?: boolean;
    title?: string;
    text?: string;
    active?: boolean;
    id?: string;
    iconName?: string;
    rows?: number;
    datalist?: string[];
    readOnly?: boolean;
    [key: string]: unknown;
}

/**
 * addXxx(...) methods accept a def object/string, or - when no def is needed - the callback itself.
 */
export type UICompDefArg = string | UICompDef | UICompCb;

export type UIElemProps = Record<string, string>;

//attrib() also sets real DOM element properties (disabled, spellcheck, ...), not just string attributes
export type AttribProps = Record<string, string | boolean>;

export type UICompCb = (comp: UIComp, comp2?: UIComp) => void;

/**
 * Used by the addLabelXxx(...) combo methods, where both comps are always provided.
 */
export type UICompPairCb = (comp: UIComp, comp2: UIComp) => void;

/**
 */
export type UIBuildConfig = {
    beforeBuild?: <T extends HTMLElement>(comp: T, ctx: { builder: UIBuilder, options?: UIElemProps }) => void;
    afterBuild?: (comp: UIComp, options?: UIElemProps) => void;
    options?: UIElemProps;
}

/**
 */
export function newUIId(prefix = "") {
    const id = Math.random().toString(32).slice(5);
    return prefix ? prefix + "-" + id : id;
}

/**
 */
export function reworkHtmlElementIds(html: string, contextIdVal: string, ignoreList: string[] = []) {
    html = html.replaceAll(/id\s*=\s*"([^"]*)"/g, (expr, val) => {
        if (!ignoreList.includes(val)) {
            return `id="${val + "-" + contextIdVal}"`
        }
        return `id="${val}"`
    });
    html = html.replaceAll(/for\s*=\s*"([^"]*)"/g, (expr, val) => {
        if (!ignoreList.includes(val)) {
            return `for="${val + "-" + contextIdVal}"`
        }
        return `for="${val}"`
    });
    return html;
}

/**
 */
export class ContextId {
    #uid = newUIId();

    get(prefix = "") {
        return prefix ? prefix + "-" + this.#uid : this.#uid;
    }
}

/**
 */
function isDataAttribute(name: string) {
    return name.startsWith("data-");
}

/**
 * <pre>
 * An experimental factory/builder to programmatically create UI components and views
 * using a combination of method chaining and chained closures.
 * 
 * A UIBuilder instance is the starting point.
 * It just serves as a dataobject and as a static function provider.
 * 
 * The actual builder objects are instances of UIComp
 * - uc = builder.newUIComp(...)
 * 
 * A UIComp is a lightweight wrapper around a domElem
 * providing the chainable methods and the closure entry.
 * 
 * - uc.addLabelButton({ text: "Command:", clazzes: ["cls1", "cls2" ...] })
 *      .attrib({"title": "Run", ...})
 *      .style({ "align-items": "flex-start", "text-align": "center", ... })
 *      ...
 *      .addFieldset( {title: "Details"}, (fieldset) => {
 *         //closure to work with the new fieldset comp
 *         fieldset.add("h3") ...
 *         ... 
 *       })
 *    ...
 * ...
 * 
 * </pre>
 */
export class UIBuilder {

    static #setterAttributes = ["name", "iconname"];
    static #valueClearableInputTypes = ["text", "password"];

    allowedDefAttributes = ["name", "title", "value"];

    //instance variables
    #defaultCompProps: DefaultCompProps = new DefaultViewCompProps();

    // true to collect UIComps not dom elems
    #UICompCollectionMode = false;

    //all elements with a varid are put to the collection
    //elem = elementCollection.<varid>
    elementCollection: JSObject = {};

    //collection for any objects
    objectCollection: JSObject = {};
    collectableAttributes = ["data-bind"];

    #UICompFactory = {
        newComp: (builder: UIBuilder, parentComp: UIComp | null, domElem: HTMLElement | null) => {
            return new UIComp(builder, parentComp, domElem);
        }
    };

    constructor(defaultCompProps?: DefaultCompProps) {
        if (defaultCompProps) {
            this.#defaultCompProps = defaultCompProps;
        }
    }

    static clearControl(domElem: HTMLElement) {

        const tagName = domElem.tagName.toLowerCase();
        let ctrl: HTMLInputElement | HTMLTextAreaElement;
        if (tagName === "input") {
            ctrl = domElem as HTMLInputElement;
            if (UIBuilder.#valueClearableInputTypes.includes(ctrl.type)) {
                ctrl.value = "";
            }
        } else if (tagName === "textarea") {
            ctrl = domElem as HTMLTextAreaElement;
            ctrl.value = "";
        }
    }

    static createDomElementFrom(html: string, tagName = "template") {
        const template = document.createElement(tagName);
        if (html) {
            template.innerHTML = html;
        }
        if (tagName.toLowerCase() == "template") {
            return (template as HTMLTemplateElement).content.firstElementChild;
        }
        return template;
    }

    static createDomElement<T extends HTMLElement>(tagName: string) {
        return document.createElement(tagName) as T;
    }

    static removeChildFrom(parent: HTMLElement, id: string) {
        const node = UIBuilder.getChildFrom(parent, id);
        if (node) { node.remove(); }
    }

    static getChildFrom(parent: HTMLElement, id: string) {
        const children = Array.from(parent.childNodes) as HTMLElement[];
        for (const child of children) {
            if (child.id === id) { return child; }
        }
        return null;
    }

    static reworkId(id?: string) {
        if (!id || id === 'undefined' || id === "") {
            return Math.random().toString(32).slice(5);
        }
        return id;
    }

    static setClassesOf(domElem: HTMLElement, clazzes: string | string[], defaultClazzes: string | string[] | null = null) {
        if (typeUtil.isArray(clazzes)) {
            (clazzes as string[]).forEach(clazz => domElem.classList.add(clazz));
        } else if (clazzes) {
            domElem.classList.add((clazzes as string).trim());
        } else if (defaultClazzes) {
            UIBuilder.setClassesOf(domElem, defaultClazzes, null);
        }
    }

    static setStyleOf(domElem: HTMLElement, styleProps: UIElemProps) {
        for (const name in styleProps) {
            domElem.style.setProperty(name, styleProps[name]);
        }
    }

    static setAttributesOf(domElem: HTMLElement, attributeProps: AttribProps) {
        for (const name in attributeProps) {
            if (UIBuilder.#setterAttributes.includes(name) || isDataAttribute(name)) {
                domElem.setAttribute(name, attributeProps[name] as string);
            } else {
                (domElem as unknown as JSObject)[name] = attributeProps[name];
            }
        }
    }

    static linkLabelToElement(label: HTMLLabelElement | UIComp, elem: HTMLElement | UIComp) {
        const labelElem = resolveElement(label);
        const targetElem = resolveElement(elem);

        labelElem.htmlFor = targetElem.id;
    }

    static loadServerStyleSheet(path: string) {
        if (!UIBuilder.queries.hasStyleSheet(path)) {
            const cssLink = document.createElement('link');
            cssLink.rel = 'stylesheet';
            cssLink.type = 'text/css';
            cssLink.href = path;
            document.head.appendChild(cssLink);
            return true;
        }
        return false;
    }

    static queries = {
        findLabelByName: (rootElem: HTMLElement, nameVal: string) => {
            return rootElem.querySelector(`label[name='${nameVal}']`);
        },
        findElementByName: (rootElem: HTMLElement, elemType: string, nameVal: string) => {
            return rootElem.querySelector(`${elemType}[name='${nameVal}']`);
        },
        findElementByName2: (rootElem: HTMLElement, elemType: string, nameVal: string) => {
            return Array.from(rootElem.querySelectorAll(elemType))
                .find(elem => (elem as unknown as { name?: string }).name === nameVal);
        },
        hasStyleSheet: (path: string) => {
            return !!document.head.querySelector(`link[rel="stylesheet"][href="${path}"]`);
        }
    }

    setElementCollection(collection: JSObject) {
        this.elementCollection = collection;
        return this;
    }

    setObjectCollection(collection: JSObject) {
        this.objectCollection = collection;
        return this;
    }

    collectingDisabled() {
        return !this.elementCollection || !this.objectCollection;
    }

    setUICompCollectionMode() {
        this.#UICompCollectionMode = true;
        return this;
    }

    collectElement(key: string, elem: HTMLElement, comp: UIComp | null = null) {
        if (this.collectingDisabled()) { return; }

        if (this.#UICompCollectionMode && comp) {
            this.elementCollection[key] = comp;
        } else {
            this.elementCollection[key] = elem;
        }
    }

    collectObject(key: string, obj: unknown, context: string | null = null) {
        if (this.collectingDisabled()) { return; }

        if (context && !this.objectCollection[context]) {
            this.objectCollection[context] = {};
        }
        if (context) {
            this.objectCollection[context][key] = obj;
        } else {
            this.objectCollection[key] = obj;
        }
    }

    forEachElement(cb: (name: string, domElem: HTMLElement) => void) {
        if (this.collectingDisabled()) { return; }

        const elements = this.elementCollection;
        const names = Object.getOwnPropertyNames(elements);
        names.forEach((name) => {
            const domElem = elements[name];
            cb(name, domElem);
        });
    }

    forEachBinding(cb: (name: string, obj: unknown) => void) {
        if (this.collectingDisabled()) { return; }

        const bindings = this.objectCollection["bindings"];
        const names = Object.getOwnPropertyNames(bindings);
        names.forEach((name) => {
            const domElem = bindings[name];
            cb(name, domElem);
        });
    }

    getDataListFor(name: string) {
        if (this.collectingDisabled()) { return; }
        return this.objectCollection[this.elementCollection[name].list.id];
    }

    setDefaultCompProps(compPropsObj: DefaultCompProps) {
        this.#defaultCompProps = compPropsObj;
        return this;
    }

    setCompPropDefaults(cb: (compProps: DefaultCompProps) => void) {
        cb(this.#defaultCompProps);
        return this;
    }

    getDefaultCompProps(): DefaultCompProps {
        return this.#defaultCompProps;
    }

    getUICompFactory() {
        return this.#UICompFactory;
    }

    setUICompFactory(factoryMethod: (builder: UIBuilder, parentComp: UIComp | null, domElem: HTMLElement | null) => UIComp) {
        this.#UICompFactory.newComp = factoryMethod;
        return this;
    }

    newUICompFor(domElem: HTMLElement) {
        return this.#UICompFactory.newComp(this, null, domElem);
    }

    newUIComp(typeId: string = "comp") {
        const defaults = this.getDefaultCompProps().get(typeId);
        const comp = this.#UICompFactory.newComp(this, null, null)
            .initialize({ elemType: defaults.elemType, "typeId": typeId });
        return comp;
    }
}

/**
 */
export class UIComp {
    domElem: HTMLElement;
    parentComp: UIComp | null;
    builder: UIBuilder;

    addingListener?: (comp: UIComp, def: UICompDef) => void;

    constructor(builder: UIBuilder, parent: UIComp | null, domElem: HTMLElement | null = null) {
        this.builder = builder;
        this.parentComp = parent;
        this.domElem = domElem as HTMLElement;
    }

    /**
     * ensure that the argument signature
     * (def=dataobject, cb=callback function)
     * is retained
     */
    resolveArgs<C extends UICompCb | UICompPairCb = UICompCb>(argDef: UICompDefArg | undefined, argCb: C | undefined): { def: UICompDef, cb: C } {
        let def: UICompDef;
        let cb = argCb;
        if (typeUtil.isFunction(argDef)) {
            cb = argDef as C;
            def = {};
        } else if (typeUtil.isString(argDef)) {
            def = { elemType: argDef as string };
        } else if (!argDef) {
            def = {};
        } else {
            def = argDef as UICompDef;
        }
        cb = cb || (() => { }) as unknown as C;
        return { def, cb };
    }

    createDomElement(def: UICompDef) {
        this.domElem = document.createElement(def.elemType || "div");
    }

    applyDefProperties(def: UICompDef) {
        if (def.clazzes) {
            this.class(def.clazzes);
        }

        const allowed = this.getBuilder().allowedDefAttributes;
        const attributes: UIElemProps = {};
        for (const key of Object.keys(def)) {
            if (allowed.includes(key) || isDataAttribute(key)) {
                attributes[key] = def[key] as string;
            }
        }

        UIBuilder.setAttributesOf(this.domElem, attributes);
    }

    applyDefaulClasses(def: UICompDef) {
        if (!def.nodefaults) {
            const defaultClasses = this.getDefaultCompProps().getClassesFor(def.typeId);
            if (defaultClasses) {
                this.class(defaultClasses);
            }
        }
    }

    applyDefaultStyle(def: UICompDef) {
        if (!def.nodefaults) {
            const defaultStyle = this.getDefaultCompProps().getStylesFor(def.typeId);
            if (defaultStyle) {
                this.style(defaultStyle);
            }
        }
    }

    addElementToTarget(targetElem: HTMLElement, elem: HTMLElement, def: UICompDef) {
        if (def.pos === "top" || def.pos === 0) {
            targetElem.prepend(elem);
        } else if (typeof def.pos === "number" && def.pos > 0) {
            targetElem.insertBefore(elem, targetElem.childNodes[def.pos]);
        } else {
            targetElem.append(elem);
        }
    }

    registerElement(def: UICompDef, elem: HTMLElement, comp: UIComp) {
        if (def.varid) {
            this.getBuilder().collectElement(def.varid, elem, comp);
        }
    }

    registerObject(key: string, obj: unknown, context: string | null = null) {
        this.getBuilder().collectObject(key, obj, context);
    }

    collectAttributesFrom(domElem: HTMLElement) {
        const names = this.getBuilder().collectableAttributes;
        let value: string | null;
        for (const name of names) {
            value = domElem.getAttribute(name);
            if (value && name === "data-bind") {
                this.registerObject(value, domElem, "bindings");
            }
        }
    }

    setAddingListener(listener: (comp: UIComp, def: UICompDef) => void) {
        this.addingListener = listener;
        return this;
    }

    linkLabelToElement(label: HTMLLabelElement | UIComp, elem: HTMLElement | UIComp) {
        UIBuilder.linkLabelToElement(label, elem);
        return this;
    }

    linkToLabel(label: HTMLLabelElement | UIComp) {
        this.linkLabelToElement(label, this.domElem);
        return this;
    }

    linkToElement(element: HTMLElement | UIComp) {
        this.linkLabelToElement(this.domElem as HTMLLabelElement, element);
        return this;
    }

    isReadOnly(def: UICompDef) {
        return Object.hasOwn(def, 'readOnly');
    }

    newDataList(domElem: HTMLElement, data: string[]) {
        const datalist = new DataList(domElem);
        datalist.setOptions(data);
        this.registerObject(datalist.listElem.id, datalist);
        return datalist;
    }

    /**
     * central method
     */
    addNewCompImpl(def: UICompDef) {
        const comp = this.getBuilder().getUICompFactory().newComp(this.getBuilder(), this, null)
            .initialize(def);

        this.addCompObjImpl(def, comp);
        return comp;
    }

    addCompObjImpl(def: UICompDef, compObj: UIComp) {
        //compObj.parent = this; // TODO: check this
        this.registerElement(def, compObj.domElem, compObj);
        this.addElementToTarget(this.domElem, compObj.domElem, def);
    }

    onAdding(comp: UIComp, def: UICompDef) {
        if (this.addingListener) {
            this.addingListener(comp, def);
        } else if (this.parentComp) {
            this.parentComp.onAdding(comp, def)
        }
    }

    finishAdd(def: UICompDef, comp: UIComp) {
        this.onAdding(comp, def);
        return this;
    }

    addContainerImpl(typeId: string, def: UICompDef, cb: (comp: UIComp) => void) {
        def.elemType = def.elemType || this.getDefaultCompProps().get(typeId)?.elemType || "span";
        def.typeId = typeId;

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    initialize(def: UICompDef) {
        this.createDomElement(def);
        this.applyDefaulClasses(def);
        this.applyDefaultStyle(def);
        this.applyDefProperties(def);
        return this;
    }

    clearClass() {
        this.domElem.setAttribute("class", "");
        return this;
    }

    class(clazzes: string | string[]) {
        UIBuilder.setClassesOf(this.domElem, clazzes);
        return this;
    }

    attrib(attribProps: AttribProps) {
        UIBuilder.setAttributesOf(this.domElem, attribProps);
        this.collectAttributesFrom(this.domElem);
        return this;
    }

    style(styleProps: UIElemProps) {
        UIBuilder.setStyleOf(this.domElem, styleProps);
        return this;
    }

    html(val?: string) {
        if (val || val === "") { this.domElem.innerHTML = val };
        return this;
    }

    title(val?: string) {
        if (val || val === "") { this.domElem.title = val };
        return this;
    }

    getBuilder(): UIBuilder {
        if (this.parentComp && !this.builder) {
            return this.parentComp.getBuilder();
        }
        return this.builder;
    }

    getDomElem() {
        return this.domElem;
    }

    getElem(key: string) {
        return this.getBuilder().elementCollection[key];
    }

    getRootComp() {
        let comp = this as UIComp; // NOSONAR
        while (comp.parentComp) { comp = comp.parentComp };
        return comp;
    }

    getDefaultCompProps(): DefaultCompProps {
        return this.getBuilder().getDefaultCompProps();
    }

    appendTo(elem: HTMLElement) {
        elem.append(this.domElem);
        return this;
    }

    prependTo(elem: HTMLElement) {
        elem.prepend(this.domElem);
        return this;
    }

    add(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addFromHtml(html: string, cb: ((elements: Element[]) => void) | null = null) {
        const template = document.createElement("template");
        template.innerHTML = html;

        const elements = [...template.content.childNodes].filter(n => n.nodeType === Node.ELEMENT_NODE) as Element[];
        for (const element of elements) {
            this.domElem.append(element);
        }
        if (cb) { cb(elements); }
        return this;
    }

    addContainer(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        return this.addContainerImpl("container", def, cb);
    }

    addColContainer(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        return this.addContainerImpl("colContainer", def, cb);
    }

    addRowContainer(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        return this.addContainerImpl("rowContainer", def, cb);
    }

    addDiv(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "div";
        def.typeId = "div";

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addSpan(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "span";
        def.typeId = "span";

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addSeparator(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "hr";
        def.typeId = "hr";

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addList(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = def.elemType || "ul";
        def.typeId = "list";

        const comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLink(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "a";
        def.typeId = "link";

        const comp = this.addNewCompImpl(def);
        if (def.text) { comp.html(def.text); }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addFontIconImpl(type: string, def: UICompDef, cb: UICompCb) {
        def.elemType = "a-icon";
        def.typeId = type;

        const comp = this.addNewCompImpl(def);
        if (def.iconName) {
            comp.domElem.setAttribute("iconname", def.iconName);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addActionIcon(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        return this.addFontIconImpl("actionIcon", def, cb);
    }

    addFontIcon(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        return this.addFontIconImpl("fontIcon", def, cb);
    }

    addFieldset(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "fieldset";
        def.typeId = def.title ? "titledFieldset" : "fieldset";

        const comp = this.addNewCompImpl(def);
        if (def.title) {
            const legend = document.createElement("legend");
            legend.innerHTML = def.title;
            comp.domElem.append(legend);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addGroup(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "fieldset";
        def.typeId = def.title ? "titledGroup" : "group";

        const comp = this.addNewCompImpl(def);
        if (def.title) {
            const legend = document.createElement("legend");
            legend.innerHTML = def.title;
            comp.domElem.append(legend);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLabel(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = def.elemType || "label";
        def.typeId = def.typeId || "label";

        const comp = this.addNewCompImpl(def);
        if (def.text) { comp.html(def.text); }
        if (def.active === false) {
            comp.style({ "pointer-events": "none" });
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addCheckBox(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "input";
        def.typeId = "checkBox";

        const comp = this.addNewCompImpl(def);
        const inputElem = comp.domElem as HTMLInputElement;
        inputElem.type = "checkbox";
        inputElem.id = UIBuilder.reworkId(def.id);
        if (def.active === false) {
            comp.style({ "pointer-events": "none" });
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addRadioButton(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "input";
        def.typeId = "radioButton";

        const comp = this.addNewCompImpl(def);
        const inputElem = comp.domElem as HTMLInputElement;
        inputElem.type = "radio";
        inputElem.id = UIBuilder.reworkId(def.id);
        if (def.active === false) {
            comp.style({ "pointer-events": "none" });
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addTextField(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "input";
        def.typeId = "textField";

        const comp = this.addNewCompImpl(def);
        const inputElem = comp.domElem as HTMLInputElement;
        inputElem.type = "text";
        inputElem.id = UIBuilder.reworkId(def.id);

        if (this.isReadOnly(def)) {
            const readOnlyClasses = this.getDefaultCompProps().getClassesFor("inputReadOnly");
            if (readOnlyClasses) { comp.domElem.classList.add(...readOnlyClasses); }
            inputElem.disabled = true;
        }

        if (def.datalist) {
            const datalist = this.newDataList(comp.domElem, def.datalist);
            this.domElem.prepend(datalist.listElem);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addButton(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "button";
        def.typeId = def.typeId == "button" || def.typeId == "tabButton" ? def.typeId : "button";

        const comp = this.addNewCompImpl(def);
        const buttonElem = comp.domElem as HTMLButtonElement;
        buttonElem.type = "button";
        buttonElem.id = UIBuilder.reworkId(def.id);

        comp.title(def.title);
        comp.html(def.text);

        if (def.iconName) {
            const iconClasses = [...Icons.getIconClasses(def.iconName), "wkv-button-icon"];
            comp.class(iconClasses);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addTabButton(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "button";
        def.typeId = "tabButton";
        return this.addButton(def, cb);
    }

    addTextArea(def?: UICompDefArg, cb?: UICompCb) {
        ({ def, cb } = this.resolveArgs(def, cb));
        def.elemType = "textarea";
        def.typeId = "textArea";

        const comp = this.addNewCompImpl(def);
        const textareaElem = comp.domElem as HTMLTextAreaElement;
        if (def.rows) { textareaElem.rows = def.rows; }
        textareaElem.id = UIBuilder.reworkId(def.id);

        if (this.isReadOnly(def)) {
            const readOnlyClasses = this.getDefaultCompProps().getClassesFor("textareaReadOnly");
            if (readOnlyClasses) { comp.domElem.classList.add(...readOnlyClasses); }
            textareaElem.disabled = true;
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLabelTextField(labelDef?: UICompDefArg, fieldDef?: UICompDefArg, cb?: UICompPairCb) {
        ({ def: labelDef, cb } = this.resolveArgs<UICompPairCb>(labelDef, cb));
        ({ def: fieldDef, cb } = this.resolveArgs<UICompPairCb>(fieldDef, cb));

        const newComp: { label: UIComp | null, textField: UIComp | null } = { label: null, textField: null };
        this.addLabel(labelDef, (comp) => { newComp.label = comp; });
        this.addTextField(fieldDef, (comp) => { newComp.textField = comp; });

        this.linkLabelToElement(newComp.label as UIComp, newComp.textField as UIComp);

        cb(newComp.label as UIComp, newComp.textField as UIComp);
        return this;
    }

    addLabelTextArea(labelDef?: UICompDefArg, areaDef?: UICompDefArg, cb?: UICompPairCb) {
        ({ def: labelDef, cb } = this.resolveArgs<UICompPairCb>(labelDef, cb));
        ({ def: areaDef, cb } = this.resolveArgs<UICompPairCb>(areaDef, cb));

        const newComp: { label: UIComp | null, textArea: UIComp | null } = { label: null, textArea: null };
        this.addLabel(labelDef, (comp) => { newComp.label = comp });
        this.addTextArea(areaDef, (comp) => { newComp.textArea = comp });

        this.linkLabelToElement(newComp.label as UIComp, newComp.textArea as UIComp);

        cb(newComp.label as UIComp, newComp.textArea as UIComp);
        return this;
    }

    addLabelButton(labelDef?: UICompDefArg, buttonDef?: UICompDefArg, cb?: UICompPairCb) {
        ({ def: labelDef, cb } = this.resolveArgs<UICompPairCb>(labelDef, cb));
        ({ def: buttonDef, cb } = this.resolveArgs<UICompPairCb>(buttonDef, cb));

        const newComp: { label: UIComp | null, button: UIComp | null } = { label: null, button: null };
        //by default deactivate label for buttons
        if (!Object.hasOwn(labelDef, "active")) {
            labelDef.active = false;
        }
        this.addLabel(labelDef, (comp) => { newComp.label = comp });
        this.addButton(buttonDef, (comp) => { newComp.button = comp });

        this.linkLabelToElement(newComp.label as UIComp, newComp.button as UIComp);

        cb(newComp.label as UIComp, newComp.button as UIComp);
        return this;
    }
}

/**
 */
export class DataList {
    ctrl: HTMLElement;
    listElem: HTMLDataListElement;

    constructor(ctrl: HTMLElement) {
        this.ctrl = ctrl;
        this.listElem = document.createElement("datalist");
        this.listElem.id = "data." + ctrl.id;
        this.ctrl.setAttribute("list", this.listElem.id);
    }

    #newOption(value: string) {
        const option = document.createElement("option");
        option.id = value;
        option.value = value;
        return option;
    }

    setOptions(values: string[]) {
        let option = null;
        values.forEach(value => {
            option = this.#newOption(value);
            this.listElem.append(option);
        });
    }

    removeOption(id: string) {
        UIBuilder.removeChildFrom(this.listElem, id);
    }

    addOption(id: string) {
        let option = UIBuilder.getChildFrom(this.listElem, id);
        if (option === null) {
            option = this.#newOption(id);
            this.listElem.prepend(option);
        }
    }
}

export function resolveElement<T extends HTMLElement>(obj: T | UIComp): T {
    if (obj instanceof UIComp) { return obj.domElem as T; }
    return obj;
}

/**
 * Shortcuts for setting event actions
 */
export function onClicked(elem: HTMLElement | UIComp, action: (evt: MouseEvent) => void) {
    resolveElement(elem).onclick = action;
}

export function onDblClicked(elem: HTMLElement | UIComp, action: (evt: MouseEvent) => void) {
    resolveElement(elem).ondblclick = action;
}

export function onChange(elem: HTMLElement | UIComp, action: (evt: Event) => void) {
    resolveElement(elem).onchange = action;
}

export function onInput(elem: HTMLElement | UIComp, action: (evt: Event) => void) {
    resolveElement(elem).oninput = action;
}

export function onKeyup(elem: HTMLElement | UIComp, action: (evt: KeyboardEvent) => void) {
    resolveElement(elem).onkeyup = action;
}

export function onKeydown(elem: HTMLElement | UIComp, action: (evt: KeyboardEvent) => void) {
    resolveElement(elem).onkeydown = action;
}

export function onFocus(elem: HTMLElement | UIComp, action: (evt: FocusEvent) => void) {
    resolveElement(elem).onfocus = action;
}

/**
 * UI default definitions
 */
export type CompPropsEntry = {
    elemType?: string;
    clazzes: string[];
    attribProps: UIElemProps;
    styleProps: UIElemProps;
};

export class DefaultCompProps {

    static makeACopyOf(source: CompPropsEntry & { clazzFilter?: unknown }): CompPropsEntry {
        const newProps = { ...source };
        newProps.clazzes = mergeArrayInto(newProps.clazzes, source.clazzes);
        newProps.attribProps = source.attribProps ? { ...source.attribProps } : {};
        newProps.styleProps = source.styleProps ? { ...source.styleProps } : {};
        delete newProps.clazzFilter;
        return newProps;
    }

    protected entries: Record<string, CompPropsEntry> = {
        blankComp: { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} },
        comp: { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} },
        colComp: { elemType: "div", clazzes: ["flex-colcomp"], attribProps: {}, styleProps: {} },
        rowComp: { elemType: "div", clazzes: ["flex-rowcomp"], attribProps: {}, styleProps: {} },

        fieldset: { clazzes: [], attribProps: {}, styleProps: {} },
        titledFieldset: { clazzes: [], attribProps: {}, styleProps: {} },
        group: { clazzes: [], attribProps: {}, styleProps: {} },
        titledGroup: { clazzes: [], attribProps: {}, styleProps: {} },
        container: { elemType: "span", clazzes: [], attribProps: {}, styleProps: {} },
        rowContainer: { elemType: "span", clazzes: ["flex-rowcomp"], attribProps: {}, styleProps: {} },
        colContainer: { elemType: "span", clazzes: ["flex-colcomp"], attribProps: {}, styleProps: {} },

        label: { clazzes: [], attribProps: {}, styleProps: {} },
        labelText: { elemType: "label-text", clazzes: [], attribProps: {}, styleProps: {} },
        link: { clazzes: [], attribProps: {}, styleProps: {} },
        list: { elemType: "ul", clazzes: [], attribProps: {}, styleProps: {} },
        actionIcon: { clazzes: [], attribProps: {}, styleProps: {} },
        fontIcon: { clazzes: [], attribProps: {}, styleProps: {} },
        button: { clazzes: [], attribProps: {}, styleProps: {} },
        tabButton: { clazzes: [], attribProps: {}, styleProps: {} },
        radioButton: { clazzes: [], attribProps: {}, styleProps: {} },
        checkBox: { clazzes: [], attribProps: {}, styleProps: {} },
        textField: { clazzes: [], attribProps: {}, styleProps: {} },
        textArea: { clazzes: [], attribProps: {}, styleProps: {} },
        hr: { clazzes: ["solid"], attribProps: {}, styleProps: {} },

        inputReadOnly: { clazzes: ["input-readonly"], attribProps: {}, styleProps: {} },
        textareaReadOnly: { clazzes: ["textarea-readonly"], attribProps: {}, styleProps: {} },
    };

    get(id?: string): CompPropsEntry {
        return this.entries[id as string];
    }

    apply(ids: string[], srcProps: Partial<CompPropsEntry>) {
        let targetProps: CompPropsEntry;
        for (const id of ids) {
            targetProps = this.entries[id];
            for (const key in srcProps) {
                if (Object.hasOwn(srcProps, key)) {
                    if (key !== "clazzes") {
                        const propKey = key as "attribProps" | "styleProps";
                        targetProps[propKey] = { ...srcProps[propKey], ...targetProps[propKey] };
                    }
                }
            }
        }
    }

    getClassesFor(id?: string): string[] | undefined {
        return id ? this.entries[id]?.clazzes : undefined;
    }
    getStylesFor(id?: string): UIElemProps | undefined {
        return id ? this.entries[id]?.styleProps : undefined;
    }
    getAttributesFor(id?: string): UIElemProps | undefined {
        return id ? this.entries[id]?.attribProps : undefined;
    }
}

/**
 */
export class DefaultViewCompProps extends DefaultCompProps {

    protected entries: Record<string, CompPropsEntry> = {
        blankComp: { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} },
        comp: { elemType: "div", clazzes: ["wkv-comp", "row-comp"], attribProps: {}, styleProps: {} },
        colComp: { elemType: "div", clazzes: ["wkv-comp", "col-comp"], attribProps: {}, styleProps: {} },
        rowComp: { elemType: "div", clazzes: ["wkv-comp", "row-comp"], attribProps: {}, styleProps: {} },

        fieldset: { clazzes: ["wkv-compset"], attribProps: {}, styleProps: {} },
        titledFieldset: { clazzes: ["wkv-compset", "wkv-compset-border"], attribProps: {}, styleProps: {} },
        group: { clazzes: ["wkv-compgroup"], attribProps: {}, styleProps: {} },
        titledGroup: { clazzes: ["wkv-compgroup", "wkv-compgroup-border"], attribProps: {}, styleProps: {} },
        container: { elemType: "span", clazzes: ["wkv-container"], attribProps: {}, styleProps: {} },
        rowContainer: { elemType: "span", clazzes: ["wkv-container", "row-container"], attribProps: {}, styleProps: {} },
        colContainer: { elemType: "span", clazzes: ["wkv-container", "col-container"], attribProps: {}, styleProps: {} },

        label: { clazzes: ["wkv-label-ctrl"], attribProps: {}, styleProps: {} },
        labelText: { elemType: "label-text", clazzes: [], attribProps: {}, styleProps: {} },
        link: { clazzes: ["wkv-link-ctrl"], attribProps: {}, styleProps: {} },
        list: { elemType: "ul", clazzes: ["wkv-list-ctrl"], attribProps: {}, styleProps: {} },
        actionIcon: { clazzes: ["wkv-action-icon"], attribProps: {}, styleProps: {} },
        fontIcon: { clazzes: ["wkv-font-icon"], attribProps: {}, styleProps: {} },
        button: { clazzes: ["wkv-button-ctrl"], attribProps: {}, styleProps: {} },
        tabButton: { clazzes: ["wkv-tab-ctrl"], attribProps: {}, styleProps: {} },
        radioButton: { clazzes: ["wkv-radiobutton-ctrl"], attribProps: {}, styleProps: {} },
        checkBox: { clazzes: ["wkv-checkbox-ctrl"], attribProps: {}, styleProps: {} },
        textField: { clazzes: ["wkv-value-ctrl"], attribProps: {}, styleProps: {} },
        textArea: { clazzes: ["wkv-textarea-ctrl"], attribProps: {}, styleProps: {} },
        hr: { clazzes: ["solid"], attribProps: {}, styleProps: {} },

        inputReadOnly: { clazzes: ["input-readonly"], attribProps: {}, styleProps: {} },
        textareaReadOnly: { clazzes: ["textarea-readonly"], attribProps: {}, styleProps: {} },
    };
}

/**
 */
export const KEY = Object.freeze({
    enter: 13, isEnter: (evt: KeyboardEvent) => evt.keyCode == KEY.enter,
    escape: 27, isEscape: (evt: KeyboardEvent) => evt.keyCode == KEY.escape
});