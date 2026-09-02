/* Authored by iqbserve.de */

/* Types */
import type { JSObject } from "types/commons";

/**
 * A simple properties class for global app values.
 * see also JPSWebAppConfigSupplier.java for backend support
 */
class Properties {

    #entries: JSObject = {
        showIntro: true,
        autoStartFeature: "",
        logLevel: 0,
        webServiceUrlRoot: "/webapi",
        webSocketUrlRoot: "/wsoapi",
        webBackendServerUrl: "",
        webAuthenticationEnabled: false,
        extensionFeaturesModule: "wb-extension-features.mjs",

        systemInfo: {},
        vcUrls: {
            loader: "https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/loader.js",
            config: "https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs"
        },

        authenticationConfig: {
            module: '/assets/js/keycloak.js',
            serverUrl: 'http://localhost:9595',
            realm: 'jamn',
            clientId: 'jps-client',
            onLoad: 'check-sso', //'check-sso' 'login-required'
            checkLoginIframe: false, // Optional: Turn off iframe check if 3rd party cookie issues
            tokenRefreshInterval: 4 * 60 * 1000 // every 4 minutes
        },

        links: {
            homePage: "https://www.iqbserve.de",
            DBConnectionExtension: "https://github.com/iqbserve/JamnJPSApp/blob/master/sample/dbconnections-extension/src/main/java/iqb/jps/sample/DBConnectionExtension.java"
        }
    };

    get(key: string, defaultVal: any = null) {
        const value = this.#entries[key];
        if (value === true || value === false) { return value; }
        return this.#entries[key] || defaultVal;
    }

    apply(values: object) {
        this.#entries = { ...this.#entries, ...values };
    }

    applyGroup(name: string, values: object) {
        if (!this.#entries[name]) { this.#entries[name] = {} }
        this.#entries[name] = { ...this.#entries[name], ...values };
    }

    showIntro() {
        return this.get("showIntro", true);
    }
    autoStartFeature() {
        return this.get("autoStartFeature", "");
    }
    webServiceUrlRoot() {
        return this.get("webServiceUrlRoot", "/webapi");
    }
    webSocketUrlRoot() {
        return this.get("webSocketUrlRoot", "/wsoapi");
    }
    webBackendServerUrl(defaultVal = null) {
        return this.get("webBackendServerUrl", defaultVal);
    }

    extensionFeaturesModule() {
        return this.get("extensionFeaturesModule", "/app/wb-extension-features.mjs");
    }

    isWebAuthenticationEnabled() {
        return this.get("webAuthenticationEnabled", false);
    }

    getLoggingProperties() {
        const pick = ["logLevel"];
        return Object.fromEntries(
            pick.map(key => [key, this.#entries[key]])
        );
    }
}

export const WbProperties = new Properties();
