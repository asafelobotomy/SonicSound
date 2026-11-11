import { watchTokens } from "../vendor/uq-tokens/uq-tokens.js";
import type { TokensRoot } from "../vendor/uq-tokens/uq-tokens.js";

/**
 * uQ's theme for the desktop shell (src-tauri/src/uq_tokens.rs reads
 * ~/.local/state/uq/theme/tokens.json and emits `uq-tokens-changed`). The
 * Android (Capacitor) and web builds have no Tauri bridge, so this does nothing
 * there and src/Styles/theme.css keeps SonicSound's own palette.
 */

export const TOKENS_COMMAND = "uq_tokens";
export const TOKENS_CHANGED_EVENT = "uq-tokens-changed";

type Unlisten = () => void;
type Invoke = (cmd: string) => Promise<unknown>;
type Listen = (event: string, handler: () => void) => Promise<Unlisten>;

/** The parts of `window.__TAURI__` used here (Tauri 1 and 2 both expose them). */
type TauriGlobal = {
    invoke?: Invoke;
    tauri?: { invoke?: Invoke };
    core?: { invoke?: Invoke };
    event?: { listen?: Listen };
};

type HostWindow = {
    __TAURI__?: TauriGlobal;
    Capacitor?: { isNativePlatform?: () => boolean };
};

export type TokensBridge = {
    read: () => Promise<string | null>;
    subscribe: (callback: () => void) => Promise<Unlisten>;
};

/**
 * The Tauri bridge for the tokens, or null outside the desktop shell (the
 * Capacitor app, a browser, or a shell built without `withGlobalTauri`).
 */
export function tauriTokensBridge(host: HostWindow): TokensBridge | null {
    if (host.Capacitor?.isNativePlatform?.()) return null;
    const tauri = host.__TAURI__;
    const invoke = tauri?.core?.invoke ?? tauri?.tauri?.invoke ?? tauri?.invoke;
    const listen = tauri?.event?.listen;
    if (typeof invoke !== "function" || typeof listen !== "function")
        return null;
    return {
        read: async () => {
            const text = await invoke(TOKENS_COMMAND);
            return typeof text === "string" ? text : null;
        },
        subscribe: (callback) => listen(TOKENS_CHANGED_EVENT, () => callback()),
    };
}

/**
 * Follows uQ's theme when running in the desktop shell. Returns the watch, or
 * null when there is no bridge (nothing is set and the app's palette applies).
 */
export function startUqTokens(
    host: HostWindow = window as unknown as HostWindow,
    root?: TokensRoot
) {
    const bridge = tauriTokensBridge(host);
    if (!bridge) return null;
    return watchTokens(root ? { ...bridge, root } : bridge);
}
