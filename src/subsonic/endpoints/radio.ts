import axios from "axios";
import { IBasicParams } from "../../Models/API/Requests/BasicParams";
import { IBackendResponse } from "../../Plugins/VLC";
import { errorResponse, okResponse } from "../errors";
import { IInternetRadioStation } from "./library";

export async function createInternetRadioStation(
    baseUrl: string,
    params: IBasicParams,
    station: { name: string; streamUrl: string; homePageUrl?: string }
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{ "subsonic-response": { status: string; error?: { message?: string } } }>(
            `${baseUrl}/rest/createInternetRadioStation`,
            {
                params: {
                    ...params,
                    name: station.name,
                    streamUrl: station.streamUrl,
                    ...(station.homePageUrl ? { homepageUrl: station.homePageUrl } : {}),
                },
            }
        );
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(ret?.data["subsonic-response"]?.error?.message);
        }
        return okResponse("");
    } catch (e: unknown) {
        const err = e as { message?: string };
        return errorResponse(err?.message ?? "Could not create radio station");
    }
}

export async function updateInternetRadioStation(
    baseUrl: string,
    params: IBasicParams,
    station: IInternetRadioStation
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{ "subsonic-response": { status: string; error?: { message?: string } } }>(
            `${baseUrl}/rest/updateInternetRadioStation`,
            {
                params: {
                    ...params,
                    id: station.id,
                    name: station.name,
                    streamUrl: station.streamUrl,
                    ...(station.homePageUrl ? { homepageUrl: station.homePageUrl } : {}),
                },
            }
        );
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(ret?.data["subsonic-response"]?.error?.message);
        }
        return okResponse("");
    } catch (e: unknown) {
        const err = e as { message?: string };
        return errorResponse(err?.message ?? "Could not update radio station");
    }
}

export async function deleteInternetRadioStation(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{ "subsonic-response": { status: string; error?: { message?: string } } }>(
            `${baseUrl}/rest/deleteInternetRadioStation`,
            { params: { ...params, id } }
        );
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(ret?.data["subsonic-response"]?.error?.message);
        }
        return okResponse("");
    } catch (e: unknown) {
        const err = e as { message?: string };
        return errorResponse(err?.message ?? "Could not delete radio station");
    }
}
