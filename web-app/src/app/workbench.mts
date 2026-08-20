/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { checkUrlAvailable, BackendServerUrl, setDisplay, setVisibility, decodeRequestParameter } from 'core/tools.mjs';
import { SplitBarHandler } from 'core/view-classes.mjs';
import { UIBuilder, DefaultCompProps, onClicked } from 'core/uibuilder.mjs';
import { WorkbenchViewManager } from 'core/view-manager.mjs';
import { WebSocketConnection, type WsoMessageListener } from 'core/websocket.mjs';
import { WsoCommonMessage } from 'app/core/data-classes.mjs';
import { NotificationHandler, Notification, type NotificationListener } from 'core/notification.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';
import { WbAppConfig } from 'config/wbapp-config.mjs';
import { callFeature } from 'app/wb-features.mjs';
import * as Webapi from 'app/core/webapi.mjs';
import * as Icons from 'core/icons.mjs';
import { registerUIWebComponents, WbTitlebar, WbStatusline, WbSidebar } from 'app/core/uicomponents.mjs';

/* Types */
import type { JSObject, JSClass, PropertiesObject, ESModule, UserProfile, DialogMessage } from 'types/commons';
import type { KeycloakIFace } from 'types/keycloak-provider';

registerUIWebComponents();

/**
 * The workbench module implements the entry point of the SPA Application.
 */

let appConfig: WbAppConfig;
let systemInfo: PropertiesObject = {};

let rootElement: HTMLElement;

let titlebar: WbTitlebar;
let sidebar: WbSidebar;
let statusline: WbStatusline;

let viewManager: WorkbenchViewManager;
let webSocket: WebSocketConnection;
let authProvider: AuthenticationProvider;

const notificationHandler = new NotificationHandler();

/**
 * Application installation function
 */
export function installApp(rootId: string) {
	rootId = rootId || (WbStartUtility ? WbStartUtility.appRootId : "unknown");
	rootElement = document.getElementById(rootId);
	return {
		build: function () {
			document.addEventListener("DOMContentLoaded", startApp);
		}
	};
};

/**
 * The workbench public function interface
 */
export const WorkbenchInterface = Object.freeze({

	confirm: (msg: DialogMessage, cb: (value: boolean) => void) => {
		viewManager.promptConfirmation(msg, cb);
	},

	sendWsoMessage: (wsoMsg: WsoCommonMessage, afterSentCb: () => void = null) => {
		return webSocket.sendMessage(wsoMsg, afterSentCb);
	},

	addWsoMessageListener: (cb: WsoMessageListener) => {
		webSocket.addMessageListener(cb);
	},

	statusLineInfo: (info: string) => {
		statusline.setInfoText(info);
	},

	titleInfo: (info: string) => {
		titlebar.setTitleText(info);
	},

	subscribeForNotification: (cb: NotificationListener) => {
		return notificationHandler.subscribe(cb);
	},

	unsubscribeNotification: (cb: NotificationListener) => {
		notificationHandler.unsubscribe(cb);
	},

	publish: (notification: Notification) => {
		notificationHandler.publish(notification);
	},

	getAuthorizationToken: async () => {
		if (authProvider) {
			return await authProvider.getToken();
		}
		return null;
	}
});

/**
 */
export function processSystemLogin() {
	if (authProvider) {
		if (authProvider.isLoggedIn()) {
			WorkbenchInterface.confirm({
				message: "<b>Log Off</b><br>Do you want to Log Off from the Server System?"
			}, (value) => value ? authProvider.doLogOff() : null);
		} else {
			authProvider.doLogIn();
		}
	}
}

/**
 * this is called after document load but before getting visible
 */
function startApp() {

	const params = decodeRequestParameter(window.location.href);
	const configName = params.has("config") ? params.get("config") : "demo";

	initAuthentication((authenticated) => {
		Webapi.doGET<string>(`${Webapi.service_get_wbappconfiguration}?name=${configName}`).then((config) => {
			applyConfig(config, authenticated);

			viewManager = new WorkbenchViewManager(document.getElementById("app-workarea"));

			initWebSocket();
			createUI();

			authProvider.notify();

			setVisibility(rootElement, true);
			sidebar.getItem(WbProperties.autoStartFeature())?.click();

			document.documentElement.style.cursor = "default";

			WbStartUtility?.close();
			Logger.info(`Workbench App started [${configName}]`);
		})
	});
}

/**
 */
function initAuthentication(appStartCb: (authenticated: boolean) => void) {
	authProvider = new AuthenticationProvider(WbProperties.get("authenticationConfig"));
	WorkbenchInterface.subscribeForNotification((msg: Notification) => {
		if (msg.type === "authentication") {
			applyLoginState();
		}
	});

	if (WbProperties.isWebAuthenticationEnabled()) {
		import(authProvider.config.module)
			.then((module) => {
				authProvider.setupProviderModule(module);
				authProvider.connect(appStartCb);
			});
	} else {
		appStartCb(false);
	}
}

/**
 */
function applyConfig(configJson: string, authenticated = false) {
	appConfig = new WbAppConfig(configJson);

	if (authenticated) {
		appConfig.getProperties().showIntro = false;
	}

	if (appConfig.getProperties()) {
		WbProperties.apply(appConfig.getProperties());
	}
	if (appConfig.getSystemInfo()) {
		WbProperties.applyGroup("systemInfo", appConfig.getSystemInfo());
		systemInfo = appConfig.getSystemInfo();
	}
}

/**
 */
function applyLoginState() {
	const workIcon = sidebar.getWorkPanelIcon("systemLogin");
	const sbarItem = sidebar.getItem("systemLogin");

	if (authProvider?.isAvailable()) {
		const authenticated = authProvider.isAuthenticated;
		const icon = workIcon;

		authProvider.getUserProfile((profile) => {
			const userProfile = profile as UserProfile;
			const userName = userProfile?.username || 'unknown';
			if (authenticated) {
				workIcon.switch({ flag: authenticated });

				icon.style.color = "green";
				icon.title = `Log Off [ ${userName} ]`;
				if (sbarItem) {
					sbarItem.innerHTML = `Log Off`;
					sbarItem.title = `Log Off [ ${userName} ]`;
				}
			} else {
				workIcon.switch({ flag: authenticated });

				icon.style.color = "";
				icon.title = "Login";
				if (sbarItem) {
					sbarItem.innerHTML = "Login";
				}
			}
		});
	} else {
		const hint = WbProperties.get("noLoginHint", "Login NOT available");
		workIcon.classList.add("item-disabled");
		workIcon.title = hint;
		sbarItem.classList.add("item-disabled");
		sbarItem.title = hint;
	}
}

/**
 */
function initWebSocket() {
	webSocket = new WebSocketConnection({
		hostUrl: BackendServerUrl(WbProperties.webSocketUrlRoot())
	}).connect();
}

/**
 */
function createUI() {

	const wbDefaults = new DefaultCompProps();
	wbDefaults.get("actionIcon").clazzes = ["wkv-action-icon"];

	titlebar = (document.getElementById("app-titlebar") as WbTitlebar)
		.build(new UIBuilder().setDefaultCompProps(wbDefaults))
		.setStepViewsUpAction(() => { viewManager.stepViewsUp(); }, "Backward step through views")
		.setStepViewsDownAction(() => { viewManager.stepViewsDown(); }, "Forward step through views");

	statusline = (document.getElementById("app-statusline") as WbStatusline)
		.build(new UIBuilder().setDefaultCompProps(wbDefaults))
		.setScmUrl(systemInfo.url);

	createSidebar();
	createIntroBox();

	WorkbenchInterface.titleInfo(`Tiny Demo - V.${systemInfo.version}`);
}

/**
 */
function createSidebar() {

	sidebar = (document.getElementById("app-sidebar") as WbSidebar)
		.setTopicDefs(appConfig.getTopicList())
		.setWorkItemDefs(appConfig.getWorkpanelItems())
		.setItemAction((featureName) => {
			callFeature(featureName, viewManager);
		})
		.build(new UIBuilder().setDefaultCompProps(new DefaultCompProps()));

	new SplitBarHandler(document.getElementById("app-sidebar-splitter"))
		.setCompBefore(sidebar)
		.setCompAfter(document.getElementById("app-workarea"))
		.setBarrierActionBefore((splitter, val) => {
			//sidebar width < x - collaps it
			if (val < 100) {
				splitter.stop();
				sidebar.toggleCollapse();
				return true; //barrier hit
			}
			return false; //barrier NOT hit
		})
		.build();
}

/**
 */
function createIntroBox() {

	const intro = document.getElementById("app-intro-overlay");

	if (!WbProperties.showIntro()) {
		setDisplay(intro, false);
		return;
	};

	onClicked(intro, (evt) => {
		setDisplay(evt.currentTarget, false);
	});

	document.getElementById("app-intro-content").innerHTML = `
		<span style="padding: 20px;">
			<h1 style="color: var(--isa-title-grayblue)">Welcome to<br>Jamn Workbench</h1>
			<span style="font-size: 18px;">
				<p>an example of using a pure Java-SE Microservice<br>together with plain Html5 and JavaScript<br></p>
				<p style="margin-bottom: 5px;">to build easily extendable 
					<a class="${Icons.github("class")}" style="color: var(--isa-title-blue);" title="Jamn All-In-One MicroService"
					target="GitHub_Repos" href="${systemInfo["readme.url"]}"><span style="margin-left: 5px;">All-in-One</span></a>
					<span>RIA/SPA Apps</span>
				</p>
				<a style="font-size: 10px; color: var(--isa-title-blue);" 
			    	href="${systemInfo["author.url"]}" title="${systemInfo.author}" target="iqbserve.de">${systemInfo.author}
				</a>
			</span>
		</span>

		<span>
			<img src="assets/intro.png" alt="Intro" style="width: 100%; height: 100%;">
		</span>
	`;
}

/**
 */
class AuthenticationProvider {
	module: ESModule;
	instance: KeycloakIFace;
	userProfile: UserProfile;

	isAuthenticated: boolean = false;
	timerId: number | null = null;

	config: JSObject;

	constructor(config: JSObject = {}) {
		this.config = { ...{ notifyingEnabled: true }, ...config };
	}

	setupProviderModule(module: ESModule) {
		this.module = module;
	}

	setNotifyingEnabled(flag: boolean) {
		this.config.notifyingEnabled = flag;
	}

	notify() {
		if (this.config.notifyingEnabled) {
			const msg = new Notification("authentication", { authenticated: this.isAuthenticated });
			WorkbenchInterface.publish(msg);
		}
	}

	connect(cb: (authenticated: boolean) => void) {
		if (this.instance == null) {
			checkUrlAvailable(this.config.serverUrl, (available) => {
				if (available) {
					const provider = this.module.default as JSClass;
					this.instance = new provider({
						url: this.config.serverUrl,
						realm: this.config.realm,
						clientId: this.config.clientId
					}) as KeycloakIFace;

					this.instance.init({
						onLoad: this.config.onLoad,
						checkLoginIframe: this.config.checkLoginIframe
					}).then((authenticated) => {
						this.isAuthenticated = authenticated;
						if (this.config.tokenRefreshInterval) {
							this.createTokenRefreshTimer();
						}
						Logger.info(`AuthenticationProvider connected [${this.timerId}]`);
						cb(authenticated);
					});
				} else {
					this.isAuthenticated = false;
					Logger.warn(`Authentication Server NOT available [${this.config.serverUrl}]`);
					cb(false);
				}
			});
		}
	}

	createTokenRefreshTimer() {
		if (this.config.tokenRefreshInterval) {
			this.timerId = setInterval(() => {
				if (this.isLoggedIn()) {
					try {
						this.instance.updateToken(30);
					} catch (e) {
						Logger.error(e);
						this.deleteTokenRefreshTimer();
					}
				}
			}, this.config.tokenRefreshInterval);
		}
	}

	deleteTokenRefreshTimer() {
		clearInterval(this.timerId);
		this.timerId = null;
	}

	getUserProfile(cb: (profile: UserProfile) => void) {
		if (this.isLoggedIn()) {
			if (this.userProfile) {
				cb(this.userProfile);
			} else {
				this.instance.loadUserProfile().then((profile) => {
					this.userProfile = profile as UserProfile;
					cb(this.userProfile);
				}).catch(() => {
					Logger.error("Failed to load user profile");
				});
			}
		} else {
			cb(null);
		}
	}

	async getToken() {
		let token = "";
		if (this.isLoggedIn()) {
			await this.instance.updateToken(30);
			token = this.instance.token;
		}
		return token;
	}

	async doLogIn() {
		if (this.instance && !this.isLoggedIn()) {
			this.isAuthenticated = this.instance.login();
		}
		this.notify();
	}

	doLogOff() {
		this.deleteTokenRefreshTimer();
		this.instance?.logout();
		this.isAuthenticated = false;
		this.notify();
	}

	isLoggedIn() {
		return this.isAuthenticated;
	}

	isAvailable() {
		return !!this.instance;
	}
}

