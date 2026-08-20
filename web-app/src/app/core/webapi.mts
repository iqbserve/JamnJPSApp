/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { BackendServerUrl } from 'core/tools.mjs';
import { WbProperties } from 'config/wbapp-properties.mjs';
import { WorkbenchInterface } from 'app/workbench.mjs';

/* Types */

/**
 * Customized webapi fetch options, extending standard fetch options
 */
export interface FetchOptions extends Omit<RequestInit, 'method' | 'body'> {
    /** 
     * If true (default), automatically parses the response as JSON.
     * If false, returns the raw text string.
     */
    parseJson?: boolean;
}

const urlRoot: string = WbProperties.webServiceUrlRoot();

function get(endpoint: string): string {
    return urlRoot + endpoint;
}

/**
 * Url name constants
 */
export const service_get_wbappconfiguration = get("/service/get-wbapp-configuration");
export const service_get_dbconnections = get("/service/get-db-connections");
export const service_save_dbconnections = get("/service/save-db-connections");
export const service_delete_dbconnections = get("/service/delete-db-connections");
export const service_upload_playground_content = get("/service/upload-playground-content");

/**
 * Method to put authorization data to request header
 */
async function getAuthorizationHeader(): Promise<Record<string, string>> {
    const header: Record<string, string> = {};
    try {
        const token: string | null = await WorkbenchInterface.getAuthorizationToken();
        if (token) {
            header['Authorization'] = `Bearer ${token}`;
        }
    } catch (e: unknown) {
        // ok since authorization is optional
        Logger.warn(e instanceof Error ? e.message : String(e));
    }
    return header;
}

/**
 * Performs a GET request.
 * @template T The expected return type of the JSON response.
 */
export async function doGET<T = unknown>(path: string, options: FetchOptions = {}): Promise<T | string> {
    const url: string = BackendServerUrl(path);
    const parseJson = options.parseJson ? options.parseJson : false;

    const authHeader = await getAuthorizationHeader();

    const fetchConfig: RequestInit = {
        ...options,
        method: "GET",
        headers: {
            "Content-Type": "application/json",
            "Accept": "application/json",
            ...authHeader,
            ...options.headers
        },
        mode: options.mode ?? "cors"
    };

    const response = await fetch(url, fetchConfig);

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} from GET ${url}`);
    }

    const resData = await response.text();
    return parseJson ? (JSON.parse(resData) as T) : resData;
}

/**
 * Performs a POST request.
 * @template T The expected return type of the JSON response.
 */
export async function doPOST<T = unknown>(path: string, requestData: unknown = {}, options: FetchOptions = {}): Promise<T | string> {
    const url: string = BackendServerUrl(path);
    const parseJson = options.parseJson ?? true;

    const authHeader = await getAuthorizationHeader();

    // If requestData isn't a string, convert it to JSON automatically
    const body = typeof requestData === 'string' ? requestData : JSON.stringify(requestData);

    const fetchConfig: RequestInit = {
        ...options,
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Accept": "application/json",
            ...authHeader,
            ...options.headers
        },
        mode: options.mode ?? "cors",
        body
    };

    const response = await fetch(url, fetchConfig);

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} from POST ${url}`);
    }

    const resData = await response.text();
    return parseJson ? (JSON.parse(resData) as T) : resData;
}
