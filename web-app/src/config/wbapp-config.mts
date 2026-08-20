/* Authored by iqbserve.de */

import type { JSObject, PropertiesObject } from "types/commons";

/* Types */
type topic = { text: string, icon: string, items: { text: string, feature: string }[] };
type workpanelItem = { text: string, icon: string, feature: string };

/**
 * A simple config class.
 */
export class WbAppConfig {

    #properties: JSObject = {};
    #systemInfo: PropertiesObject = {};
    #topicList: topic[] = [];
    #workpanelItems: workpanelItem[] = [];

    constructor(json: string) {
        const config = JSON.parse(json);
        this.#properties = config.properties;
        this.#topicList = config.topicList;
        this.#workpanelItems = config.workpanelItems;
        this.#systemInfo = config.systemInfo;
    }

    getProperties() {
        return this.#properties;
    }

    getTopicList() {
        return this.#topicList;
    }

    getWorkpanelItems() {
        return this.#workpanelItems;
    }

    getSystemInfo() {
        return this.#systemInfo;
    }

}