import { buildAuthParams, ApiVersion } from "../subsonic/auth";
import { negotiateApiVersion, removeTrailingSlash } from "../subsonic/client";
import { errorResponse, okResponse } from "../subsonic/errors";
import { getSpotifyAccessToken } from "../subsonic/spotify";
import { IAccount, IAppContext } from "../Models/AppContext";
import { IBackendResponse } from "../Plugins/VLC";

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

export function loadStoredContext(): IAppContext {
    const raw = localStorage.getItem("serverCreds");
    if (!raw) return JSON.parse(JSON.stringify(EMPTY_CONTEXT));
    return JSON.parse(raw);
}

export function persistContext(context: IAppContext): void {
    localStorage.setItem("serverCreds", JSON.stringify(context));
}

export async function performLogin(options: {
    username: string;
    password: string;
    url: string;
    usePlaintext: boolean;
    existing: IAppContext;
}): Promise<IBackendResponse<IAccount> & { context?: IAppContext; apiVersion?: ApiVersion }> {
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
    return { ...okResponse(creds), context: newContext, apiVersion: negotiated.version };
}

export function basicParamsFor(
    context: IAppContext,
    account?: IAccount
) {
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
    persistContext(next);
    return next;
}
