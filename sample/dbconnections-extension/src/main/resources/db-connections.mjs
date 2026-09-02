/* Authored by iqbserve.de */
import { Logger } from 'core/logging.mjs';
import { WorkView } from 'core/view-classes.mjs';
import { UIBuilder, onClicked } from 'core/uibuilder.mjs';
import { WorkViewHtml } from 'core/view-templates.mjs';
import * as Webapi from 'app/core/webapi.mjs';
import * as Icons from 'core/icons.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';
import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';
import { SimpleCrudComp } from 'app/core/uicomponents.mjs';
export class DbConnectionDef {
    name = "";
    type = "";
    url = "";
    user = "";
    owner = "";
    constructor(name) {
        this.name = name;
    }
}
/**
 * A Database connection WorkView created in javascript using a builder.
 */
class DbConnectionsView extends WorkView {
    builder;
    //ui element collections
    elem = {};
    //the connection data objects
    connections;
    DBComp;
    constructor(id) {
        super(id, null);
        this.viewSource.setHtml(WorkViewHtml());
    }
    initialize() {
        super.initialize();
        this.setTitle("Database Connections");
        this.getConnections(() => {
            this.extendViewMenu();
            this.createUI();
            this.setVisible(true);
            this.isInitialized = true;
        });
    }
    /** START UI *****************************************************************/
    createUI() {
        this.builder = new UIBuilder()
            //the mirror collection variable
            .setElementCollection(this.elem)
            //define some style defaults
            .setCompPropDefaults((props) => {
            props.get("label").styleProps = { "width": "70px" };
            props.get("button").styleProps = { "width": "156px", "height": "26px" };
            props.get("textField").styleProps = { "width": "150px" };
        });
        //local shortcut to avoid this.
        const builder = this.builder;
        //get a ui comp object for the workarea for styling
        //and adding a title
        const compSet = builder.newUICompFor(this.viewWorkarea)
            .style({ "gap": "10px" })
            .add("h2", (title) => {
            title.style({ "font-weight": "normal", "user-select": "none" })
                .html("Define and edit database connection properties");
        }).getDomElem();
        this.createSelectionPanel(builder, compSet);
        this.createPropertiesPanel(builder, compSet);
        this.createSidePanel(builder);
    }
    /**
     */
    createSelectionPanel(builder, target) {
        this.DBComp = UIBuilder.createDomElement(SimpleCrudComp.TagName)
            .build(builder)
            .appendTo(target)
            .setItems(Object.getOwnPropertyNames(this.connections))
            .setLabelText("Name:")
            .setPlaceholder("db connection name")
            .setSelectionChangedAction(() => {
            this.writeDataToView();
        })
            .setClearAction(() => {
            this.clearViewData();
        }, "Clear current selection and data")
            .setSaveAction(() => {
            this.saveConnection();
        }, "Save current database connection")
            .setDeleteAction(() => {
            this.deleteConnection();
        }, "Delete current database connection");
    }
    /**
     */
    createPropertiesPanel(builder, target) {
        const hgap = "20px";
        let propertiesCompSet;
        builder.newUIComp()
            .addFieldset({ title: "Properties" }, (fieldset) => {
            fieldset.style({ width: "700px", "row-gap": "10px" });
            propertiesCompSet = fieldset.getDomElem();
        });
        target.append(propertiesCompSet);
        builder.newUIComp()
            .addLabelTextField({ text: "DB Url:" }, { varid: "tfDbUrl" }, (label, tfDbUrl) => {
            tfDbUrl.style({ width: "600px" })
                .attrib({ placeholder: "url like e.g. - jdbc:oracle:thin:@localhost:1521/XEPDB1", "data-bind": "url" });
        })
            .appendTo(propertiesCompSet);
        builder.newUIComp()
            .addLabelTextField({ text: "User:" }, { varid: "tfUser" }, (label, tfUser) => {
            tfUser.attrib({ placeholder: "name", "data-bind": "user" });
        })
            .addTextField({ varid: "tfOwner" }, (tfOwner) => {
            tfOwner.style({ "margin-left": hgap })
                .attrib({ placeholder: "optional owner", "data-bind": "owner" });
        })
            .appendTo(propertiesCompSet);
        builder.newUIComp()
            .style({ "align-items": "baseline" })
            .addLabelTextField({ text: "Password:" }, { varid: "tfPwd" }, (label, tfPwd) => {
            tfPwd.attrib({ type: "password", placeholder: "********" });
        })
            .addButton({ text: "Test", title: "Test connection", varid: "pbTest" }, (pbTest) => {
            pbTest.style({ "margin-left": hgap });
            onClicked(pbTest, () => { this.runTestDbConnection(); });
        })
            .addTextArea({ varid: "tfTestResult", rows: "1", readOnly: true }, (tfTestResult) => {
            tfTestResult.style({ "overflow": "hidden", "margin-left": hgap, "text-align": "left", "align-self": "center", "min-width": "80px", "width": "80px" })
                .attrib({ placeholder: "<result>", title: "Test Result" });
        })
            .appendTo(propertiesCompSet);
    }
    /**
     */
    createSidePanel(builder) {
        //creating a side panel content
        //using mainly plain html
        const makeLI = (iconClass, text) => {
            return `<li style='margin-block-end: 5px;'><span class="${iconClass}"></span> ${text}</li>`;
        };
        const sidePanelComp = builder.newUIComp("blankComp")
            .style({ "padding": "20px" })
            .addFromHtml(`<span style="display: flex; align-items: center;">
					<i class="${Icons.infoc('class')}" style="font-size: 22px; color: var(--info-light-green);"></i>
    				<h3 style="margin-left: 20px; font-weight: normal; user-select: none;">DB Connections View</h3>
				</span>
				<p>Extension sample view to create and edit database connection information.</p>
				<p>Connections can be created and edited under a unique name.<br>After typing or selecting an existing name,<br>the connection data is loaded and displayed.</p>
				<ul>
					${makeLI(Icons.eraser("class"), 'clears the current selection and data')}
					${makeLI(Icons.save("class"), 'saves the current data')}
					${makeLI(Icons.trash("class"), 'deletes the current connection')}
				</ul >
				<span>Java Source Code: </span><a class="wkv-link-ctrl" href="${WbProperties.get('links').DBConnectionExtension}" target="iqbserve.code">SampleExtension.java<a>
				`);
        this.installSidePanel(sidePanelComp.getDomElem()).setWidth("350px");
        //show it opened
        this.toggleSidePanel();
    }
    extendViewMenu() {
        this.viewHeader.menu((menu) => {
            menu.addItem("Clear View", () => {
                this.clearViewData();
            }, { separator: "top" });
        });
    }
    /** END UI *****************************************************************/
    getConnections(cb) {
        if (this.connections) {
            cb(this.connections);
        }
        else {
            //load the data from server
            Webapi.doPOST(Webapi.service_get_dbconnections).then((response) => {
                //connections are sent as an array - create an object from it
                this.connections = {};
                response.connections.forEach((item) => this.connections[item.name] = item);
                cb(this.connections);
            });
        }
    }
    getCurrentConnection() {
        const key = this.DBComp.getFieldValue();
        if (key && this.connections[key]) {
            return this.connections[key];
        }
        return null;
    }
    newConnection() {
        const key = this.DBComp.getFieldValue();
        if (key && !this.connections[key]) {
            return new DbConnectionDef(key);
        }
        return null;
    }
    clearViewData(excludes = []) {
        if (!excludes.includes(this.DBComp)) {
            this.DBComp.setFieldValue("");
        }
        this.builder.forEachElement((name, ctrl) => {
            if (!excludes.includes(ctrl)) {
                UIBuilder.clearControl(ctrl);
            }
        });
        this.showConnectionTestResult();
    }
    writeDataToView() {
        const excludes = [this.DBComp];
        const dbCon = this.getCurrentConnection();
        if (dbCon) {
            this.builder.forEachBinding((name, ctrl) => {
                if (dbCon[name]) {
                    ctrl.value = dbCon[name];
                    excludes.push(ctrl);
                }
            });
            this.clearViewData(excludes);
        }
    }
    readDataFromView(connection) {
        const dbCon = connection || this.getCurrentConnection();
        if (dbCon) {
            this.builder.forEachBinding((name, ctrl) => {
                dbCon[name] = ctrl.value;
            });
        }
    }
    saveConnection() {
        const dbCon = this.getCurrentConnection() || this.newConnection();
        if (dbCon) {
            this.readDataFromView(dbCon);
            const request = JSON.stringify({ connections: [dbCon] });
            Webapi.doPOST(Webapi.service_save_dbconnections, request).then((response) => {
                if (response.status === "ok") {
                    if (!this.connections[dbCon.name]) {
                        this.connections[dbCon.name] = dbCon;
                        this.DBComp.addItem(dbCon.name);
                    }
                    Logger.info("Saved: ok");
                }
                else if ((response.status === "error")) {
                    Logger.error("Save: error: " + response.error);
                }
            });
        }
    }
    deleteConnection() {
        const dbCon = this.getCurrentConnection();
        if (dbCon) {
            WbApp.confirm({
                message: `<b>Delete DB Connection</b><br>Do you want to delete <b>[${dbCon.name}]</b> connection?`
            }, (val) => {
                if (val) {
                    const request = JSON.stringify({ connections: [dbCon] });
                    Webapi.doPOST(Webapi.service_delete_dbconnections, request).then((response) => {
                        if (response.status === "ok") {
                            delete this.connections[dbCon.name];
                            this.DBComp.removeItem(dbCon.name);
                            this.clearViewData();
                            Logger.info("Deleted: ok");
                        }
                    });
                }
            });
        }
    }
    runTestDbConnection() {
        const userId = this.elem.tfUser.value.trim();
        const pwd = this.elem.tfPwd.value.trim();
        const demoPwd = "XyZ$1a2b3c";
        if (userId) {
            if (demoPwd == pwd) {
                this.showConnectionTestResult(true);
            }
            else {
                this.showConnectionTestResult(false, `Connection refused - invalid credentials - demo password: ${demoPwd}`);
            }
        }
        else {
            this.showConnectionTestResult();
        }
    }
    showConnectionTestResult(status = -1, text = "") {
        const ctrl = this.elem.tfTestResult;
        const okProps = { color: "green", resize: "none", width: "80px", height: ctrl.style["min-height"] };
        if (status === false) { // NOSONAR
            ctrl.value = "FAILURE - " + text + "\n\n" + new Error("Connection test failed").stack;
            UIBuilder.setStyleOf(ctrl, { color: "red", resize: "auto", width: "550px" });
        }
        else if (status === true) { // NOSONAR
            ctrl.value = "Success";
            UIBuilder.setStyleOf(ctrl, okProps);
        }
        else {
            ctrl.value = "";
            okProps.color = "";
            UIBuilder.setStyleOf(ctrl, okProps);
        }
    }
}
//export this view component as singleton instance
const viewInstance = new DbConnectionsView("dbConnectionsView");
export function getView() {
    return viewInstance;
}
