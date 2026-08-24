import { buildAuthParams, ApiVersion } from "../subsonic/auth";
import { negotiateApiVersion, removeTrailingSlash } from "../subsonic/client";
import { errorResponse, okResponse } from "../subsonic/errors";
import { getSpotifyAccessToken } from "../subsonic/spotify";
import { IAccount, IAppContext } from "../Models/AppContext";
import { IBackendResponse } from "../Plugins/VLC";

const STORAGE_KEY = "serverCreds";

/** In-memory fallback when localStorage is unavailable (SSR/tests/private mode). */
const memoryStore = new Map<string, string>();

const EMPTY_CONTEXT: IAppContext = {
    activeAccount: {
        username: null,
        password: "",
        url: "",
        type: "",
        usePlaintext: false,
    },
    accounts: [],
    spotifyToken: "",
};

/** Persist account metadata; passwords stay local to this device only. */
function readRaw(): string | null {
    try {
        if (typeof localStorage !== "undefined") {
            return localStorage.getItem(STORAGE_KEY);
        }
    } catch {
        /* private mode / quota */
    }
    return memoryStore.get(STORAGE_KEY) ?? null;
}

function writeRaw(value: string): void {
    try {
        if (typeof localStorage !== "undefined") {
            localStorage.setItem(STORAGE_KEY, value);
            memoryStore.set(STORAGE_KEY, value);
            return;
        }
    } catch {
        /* private mode / quota */
    }
    memoryStore.set(STORAGE_KEY, value);
}

export function loadStoredContext(): IAppContext {
    const raw = readRaw();
    if (!raw) return JSON.parse(JSON.stringify(EMPTY_CONTEXT));
    try {
        return JSON.parse(raw) as IAppContext;
    } catch {
        return JSON.parse(JSON.stringify(EMPTY_CONTEXT));
    }
}

export function persistContext(context: IAppContext): void {
    writeRaw(JSON.stringify(context));
}

export function clearStoredContext(): void {
    memoryStore.delete(STORAGE_KEY);
    try {
        if (typeof localStorage !== "undefined") {
            localStorage.removeItem(STORAGE_KEY);
        }
    } catch {
        /* ignore */
    }
}

export async function performLogin(options: {
    username: string;
    password: string;
    url: string;
    usePlaintext: boolean;
    existing: IAppContext;
}): Promise<
    IBackendResponse<IAccount> & {
        context?: IAppContext;
        apiVersion?: ApiVersion;
    }
> {
    const negotiated = await negotiateApiVersion(
        options.url,
        options.username,
        options.password,
        options.usePlaintext
    );
    if (!negotiated.ok) {
        return errorResponse(negotiated.message);
    }

    let spotifyToken = "";
    try {
        spotifyToken = await getSpotifyAccessToken();
    } catch {
        spotifyToken = "";
    }

    const creds: IAccount = {
        username: options.username,
        password: options.password,
        url: removeTrailingSlash(options.url),
        type: negotiated.type,
        usePlaintext: options.usePlaintext,
        apiVersion: negotiated.version,
    };

    const accounts = options.existing.accounts.some((s) => s.url === options.url)
        ? [
              ...options.existing.accounts.filter((s) => s.url !== options.url),
              creds,
          ]
        : [...options.existing.accounts, creds];

    const newContext: IAppContext = {
        activeAccount: creds,
        accounts,
        spotifyToken,
    };
    persistContext(newContext);
    return {
        ...okResponse(creds),
        context: newContext,
        apiVersion: negotiated.version,
    };
}

export function basicParamsFor(context: IAppContext, account?: IAccount) {
    const c = account ?? context.activeAccount;
    const version =
        (c as IAccount & { apiVersion?: string }).apiVersion || "1.16.1";
    return buildAuthParams(c, version);
}

export function deleteAccountFromContext(
    context: IAppContext,
    url: string
): IAppContext {
    const accounts = context.accounts.filter((s) => s.url !== url);
    const activeAccount =
        context.activeAccount.url === url
            ? accounts[0] ?? EMPTY_CONTEXT.activeAccount
            : context.activeAccount;
    const next = { ...context, accounts, activeAccount };
    if (!next.activeAccount.username) {
        clearStoredContext();
        return next;
    }
    persistContext(next);
    return next;
}
