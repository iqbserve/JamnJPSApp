/* Authored by iqbserve.de */

import type { JSObject, PropertiesObject } from "types/commons";

/* Types */
export type SBarItemDef = { text: string, feature: string };
export type SBarTopicDef = { text: string, icon?: string, items: SBarItemDef[] };
type workpanelItem = { text: string, icon: string, feature: string };

/**
 * A simple config class.
 */
export class WbAppConfig {

    #properties: JSObject = {};
    #systemInfo: PropertiesObject = {};
    #topicList: SBarTopicDef[] = [];
    #workpanelItems: workpanelItem[] = [];
    #topicIcons: PropertiesObject = {};

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

    getTopicIcon(topic : string) : string {
        return this.#topicIcons[topic] || "";
    }

    setTopicIcon(topic: string, icon: string) {
        this.#topicIcons[topic] = icon;
    }

    buildTopicList() {
        this.#topicList.forEach(topic => {
            if (!topic.icon || topic.icon === "") {
                topic.icon = this.getTopicIcon(topic.text);
            }
        });
    }
}