import axios from "axios";
import { IBackendResponse } from "../../Plugins/VLC";
import { IBasicParams } from "../../Models/API/Requests/BasicParams";
import { errorResponse, okResponse } from "../errors";

export async function starSong(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{
            "subsonic-response": { status: string; error?: { message?: string } };
        }>(`${baseUrl}/rest/star`, { params: { ...params, id } });
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(ret?.data["subsonic-response"]?.error?.message);
        }
        return okResponse("");
    } catch (e: unknown) {
        return errorResponse((e as { message?: string })?.message ?? "star failed");
    }
}

export async function unstarSong(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{
            "subsonic-response": { status: string; error?: { message?: string } };
        }>(`${baseUrl}/rest/unstar`, { params: { ...params, id } });
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(ret?.data["subsonic-response"]?.error?.message);
        }
        return okResponse("");
    } catch (e: unknown) {
        return errorResponse(
            (e as { message?: string })?.message ?? "unstar failed"
        );
    }
}
