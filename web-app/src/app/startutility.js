/* Authored by iqbserve.de */

/**
 * The object is mainly an error monitor to react to boostrapping errors,
 * especially module loading errors.
 */
globalThis.WbStartUtility = (function () {
	class WbStartUtilityClass {
		static instance = new WbStartUtilityClass();

		appRootId;

		#onPromiseError = (event) => {
			this.close();
			this.showError(event.reason);
		};

		#onOtherError = (event) => {
			this.close();
			this.showError(event.error);
		};

		setAppRootId(id) {
			this.appRootId = id;
		}

		activate() {
			this.close();
			window.addEventListener("unhandledrejection", this.#onPromiseError);
			window.addEventListener("error", this.#onOtherError);
			return this;
		}

		close() {
			window.removeEventListener("unhandledrejection", this.#onPromiseError);
			window.removeEventListener("error", this.#onOtherError);
		}

		showError(error) {
			document.documentElement.style.cursor = "default";
			console.error(error);
			let rootElement = document.getElementById(this.appRootId);
			if (rootElement) {
				rootElement.style.display = "none";
			}
			let errorElem = document.createElement("div");
			errorElem.className = "initializationError";
			errorElem.innerHTML = `
			<h1>Application initialization Error</h1>
			<p style="font-size: 20px;">Please excuse us. Unfortunately an unexpected error occurred.</p>
			<p style="font-size: 16px; color: firebrick;">Error message: [${error.message}]</p>
			<p style="font-size: 20px;">For further details, please refer to the console output.</p>
		`;
			document.body.style.overflow = "hidden";
			document.body.appendChild(errorElem);
		}
	}

	return WbStartUtilityClass.instance.activate();
})();
