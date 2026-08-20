/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { WsoCommonMessage } from 'core/data-classes.mjs';
import { WorkbenchInterface as WbApp } from 'app/workbench.mjs';

/* Types */
import type { PropertiesObject } from 'types/commons';

export type WsoMessageListener = (wsoMsg: WsoCommonMessage) => void;

/**
 * A simple WebSocket implementation.
 * 
 * The Data-IO uses WsoCommonMessage objects
 * that are serialized/deserialized to+from JSON.
 */
export class WebSocketConnection {

    #props: PropertiesObject;
    #socket: WebSocket;
    #listener: { [key: string]: WsoMessageListener[] };

    constructor(props: PropertiesObject) {
        this.#props = props;
        this.#listener = { "any": [] };
    }

    isConnected() {
        return !(this.#socket == null || this.#socket.readyState == WebSocket.CLOSED);
    }

    connect() {
        if (this.isConnected()) {
            Logger.warn("Warning: WebSocket already connected");
        } else {
            this.#socket = new WebSocket(this.#props.hostUrl);

            // Event listener 
            this.#socket.onopen = () => {
                Logger.info("WebSocket connection [opened]");
            };

            this.#socket.onmessage = (event: MessageEvent) => {
                this.#onMessage(event);
            };

            this.#socket.onclose = () => {
                this.#socket = null;
                Logger.info("WebSocket connection [closed]");
            };

            this.#socket.onerror = () => {
                this.#socket = null;
                Logger.error("WebSocket connection error");
                this.#onMessage(event);
            };
        }
        return this;
    }

    close() {
        if (this.isConnected()) {
            this.#socket.close();
            this.#socket = null;
        }
    }

    /**
     * Expects WsoCommonMessage objects
     */
    sendMessage(wsoMsg: WsoCommonMessage, afterSentCb: () => void = null) {
        if (wsoMsg instanceof WsoCommonMessage) {
            if (this.isConnected()) {
                const msg = this.#createWsoMessageString(wsoMsg);
                this.#socket.send(msg);
                if (afterSentCb) { afterSentCb(); }
                return true;
            } else {
                Logger.warn("WebSocket NOT connected");
                WbApp.confirm({ message: "The Server Connection was closed.<br>Would you like to try a reconnect?" },
                    (value) => value ? this.connect() : null);
            }
        } else {
            throw new TypeError("WsoCommonMessage type expected");
        }
        return false;
    }

    addMessageListener(cb: WsoMessageListener, subject: string = "any") {
        if (Object.hasOwn(this.#listener, subject)) {
            if (!this.#listener[subject].includes(cb)) {
                this.#listener[subject].push(cb);
            }
        } else {
            this.#listener[subject] = [cb];
        }
    }

    /**
     * Expects WsoCommonMessage as JSON
     */
    #onMessage(event: MessageEvent | Event) {
        const subject = "any";
        let wsoMsg = new WsoCommonMessage("");

        if (event.type === "error") {
            wsoMsg.setStatusError("connection error");
        } else {
            const msg = JSON.parse((event as MessageEvent).data);
            wsoMsg = Object.assign(wsoMsg, msg);
        }

        this.#listener[subject].forEach((cb: WsoMessageListener) => cb(wsoMsg));
    }

    #createWsoMessageString(wsoMsg: WsoCommonMessage) {
        return JSON.stringify(wsoMsg);
    }
}




