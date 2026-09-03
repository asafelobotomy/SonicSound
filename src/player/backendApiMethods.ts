import { backendApi } from "./backendApi";
import { backendRemote } from "./backendRemote";

/** Methods mixed onto Backend via explicit assignment in the constructor. */
export type BackendApiMethods = typeof backendApi & typeof backendRemote;
