import axios, { AxiosResponse } from "axios";
import { IBasicParams } from "../Models/API/Requests/BasicParams";
import { ISubsonicResponse } from "../Models/API/Responses/SubsonicResponse";
import {
    API_VERSION_CANDIDATES,
    ApiVersion,
    buildAuthParams,
    isVersionRejectedMessage,
} from "./auth";
import { IAccount } from "../Models/AppContext";

type Envelope<T extends ISubsonicResponse = ISubsonicResponse> = {
    "subsonic-response": T;
};

export async function getSubsonic<T extends ISubsonicResponse>(
    baseUrl: string,
    endpoint: string,
    params: IBasicParams
): Promise<AxiosResponse<Envelope<T>>> {
    const url = `${baseUrl.replace(/\/+$/, "")}/rest/${endpoint}`;
    return axios.get<Envelope<T>>(url, { params });
}

/**
 * Probe the server with getArtists (or ping-compatible call) across API
 * versions until one is accepted. Returns the working version + response type.
 */
export async function negotiateApiVersion(
    url: string,
    username: string,
    password: string,
    usePlaintext: boolean
): Promise<{
    version: ApiVersion;
    type: string;
    ok: true;
} | {
    ok: false;
    message: string;
}> {
    const account = {
        username,
        password,
        usePlaintext,
    } as IAccount;

    let lastMessage = "Unable to negotiate a compatible Subsonic API version.";

    for (const version of API_VERSION_CANDIDATES) {
        const params = buildAuthParams(account, version);
        try {
            const ret = await getSubsonic(url, "getArtists", params);
            const body = ret.data["subsonic-response"];
            if (ret.status === 200 && body?.status === "ok") {
                return {
                    ok: true,
                    version,
                    type: body.type ?? "subsonic",
                };
            }
            const msg = body?.error?.message ?? ret.statusText;
            lastMessage = msg;
            if (!isVersionRejectedMessage(msg)) {
                return { ok: false, message: msg };
            }
        } catch (e: unknown) {
            const err = e as { message?: string };
            lastMessage =
                err?.message ??
                "There was an error connecting to the server.";
            // Network errors: no point trying other versions
            if (
                typeof lastMessage === "string" &&
                !isVersionRejectedMessage(lastMessage)
            ) {
                return { ok: false, message: lastMessage };
            }
        }
    }

    return { ok: false, message: lastMessage };
}

export function removeTrailingSlash(str: string): string {
    return str.replace(/\/+$/, "");
}
