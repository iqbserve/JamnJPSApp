/* Authored by iqbserve.de */

import { WorkView, StandardDialog } from 'core/view-classes.mjs';
import { DialogMessage } from 'types/commons';

/* Types */
type ViewEntry = { view: WorkView, cart: HTMLElement }

/**
 * <pre>
 * A simple manager for showing views in the app working area.
 * </pre>
 */
export class WorkbenchViewManager {

	#workarea: HTMLElement;
	#registeredViews: Record<string, ViewEntry>;

	#standardDlg: StandardDialog;

	constructor(workareaElem: HTMLElement) {
		this.#workarea = workareaElem;
		this.#registeredViews = {};

		this.#standardDlg = new StandardDialog(document.getElementsByTagName("main")[0]);
	}

	openView(view: WorkView) {
		if (view instanceof WorkView) {
			this.#resolveViewRegistration(view, (viewEntry: ViewEntry) => {
				this.#openViewImpl(viewEntry);
			});
		} else {
			throw new TypeError("WorkView type expected");
		}
	}

	closeView(view: WorkView | string) {
		let viewEntry: ViewEntry;
		if (view instanceof WorkView) {
			viewEntry = this.#registeredViews[view.id];
		} else {
			viewEntry = this.#registeredViews[view];
		}
		// view is expected to do an internal closing itself 
		// and return true if it was closeabel and did close
		if (viewEntry.view.close()) {
			// then this viewmanager does the visual close
			this.#setViewCartVisible(viewEntry.cart, false);
		}
	}

	#resolveViewRegistration(view, cb) {
		let viewEntry = this.#registeredViews[view.id];
		if (viewEntry) {
			cb(viewEntry);
		} else {
			viewEntry = { view: view, cart: null };
			this.#registeredViews[view.id] = viewEntry;
			if (Object.hasOwn(view, "viewManager") && !view.viewManager) {
				view.viewManager = this;
			}

			view.getViewElement((element) => {
				const viewCart = this.#createViewCartridge(view.id, element);
				this.#workarea.prepend(viewCart);
				cb(viewEntry);
			});
		}
	}

	#openViewImpl(viewEntry: ViewEntry) {
		this.#closeAllCloseableViews();
		viewEntry.view.open();
		this.moveView(viewEntry.view, "1");
		this.#setViewCartVisible(viewEntry.cart, true);
		this.#scrollToTop();
	}

	//the container dom element used by the view manager
	#createViewCartridge(viewId, viewElement) {
		const viewCart = document.createElement("div");
		viewCart.id = "view.cartridge." + viewId;
		viewCart.style = "visibility: visible; display: block;"
		viewCart.appendChild(viewElement);

		this.#registeredViews[viewId].cart = viewCart;
		return viewCart;
	}

	#setViewCartVisible(viewCart, flag) {
		if (flag) {
			viewCart.style["display"] = "block";
			viewCart.style["visibility"] = "visible";
		} else if (this.#workarea.children.length > 0) {
			viewCart.style["display"] = "none";
		}
	}

	#closeAllCloseableViews() {
		for (const key in this.#registeredViews) {
			const viewEntry = this.#registeredViews[key];
			if (viewEntry.cart) {
				this.closeView(viewEntry.view);
			}
		}
	}

	#getVisibleChildren() {
		const children: HTMLElement[] = [];
		for (const child of Array.from(this.#workarea.children) as HTMLElement[]) {
			if (child.style.display == "block") {
				children.push(child);
			}
		}
		return children;
	}

	#scrollToTop() {
		this.#workarea.scrollTop = 0;
	}

	stepViewsDown() {
		const children = this.#getVisibleChildren();
		if (children.length > 1) {
			this.#workarea.insertBefore(children.at(-1), children[0]);
			this.#scrollToTop();
		}
	}

	stepViewsUp() {
		const children = this.#getVisibleChildren();
		if (children.length > 1) {
			this.#workarea.insertBefore(children[0], null);
			this.#scrollToTop();
		}
	}

	moveView(view: WorkView, position: string) {
		const elemCount = this.#workarea.children.length;
		const viewCart = this.#registeredViews[view.id].cart;

		if (Number.isNaN(Number.parseInt(position))) {
			let idx: number;
			if (position === "up") {
				idx = Array.prototype.indexOf.call(this.#workarea.children, viewCart) - 1;
			} else if (position === "down") {
				idx = Array.prototype.indexOf.call(this.#workarea.children, viewCart) + 1;
			} else {
				return;
			}
			//always add 1
			//cause position is expected to be a human counter 1...n 
			//NOT array idx 0...n
			position = (idx + 1).toString();
		}

		let pos = Number.parseInt(position);
		pos = pos <= 0 ? 1 : pos;

		if (elemCount > 1) {
			if (pos >= elemCount) {
				viewCart.remove();
				this.#workarea.appendChild(viewCart);
			} else if (pos - 1 >= 0) {
				viewCart.remove();
				this.#workarea.insertBefore(viewCart, this.#workarea.children[pos - 1]);
			}
		}
	}

	promptUserInput(msg: DialogMessage, cb: (value: string) => void) {
		this.#standardDlg.openInput(msg, cb);
	}

	promptConfirmation(msg: DialogMessage, cb: (value: boolean) => void) {
		this.#standardDlg.openConfirmation(msg, cb);
	}
}

