/* Authored by iqbserve.de */

/* Types */
export type NotificationListener = (msg: Notification) => void;

/**
 */
export class NotificationHandler {

    #listeners = new Set<NotificationListener>();

    constructor() {
    }

    subscribe(cb: NotificationListener) {
        this.#listeners.add(cb);
        return () => this.#listeners.delete(cb);
    }

    unsubscribe(cb: NotificationListener) {
        this.#listeners.delete(cb);
    }

    publish(msg: Notification) {
        this.#listeners.forEach(cb => cb(msg));
    }
}

/**
 */
export class Notification {
    type: string;
    value: unknown;

    constructor(type: string, value: unknown) {
        this.type = type;
        this.value = value;
    }
}
