import { backendApi } from "./backendApi";

/** Methods mixed onto Backend via Object.assign(backendApi). */
export type BackendApiMethods = typeof backendApi;
