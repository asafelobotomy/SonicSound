import axios from "axios";
import { IBasicParams } from "../../Models/API/Requests/BasicParams";
import {
    IPlaylist,
    IPlaylistResponse,
    IPlaylistsResponse,
} from "../../Models/API/Responses/IPlaylistsResponse";
import { IBackendResponse } from "../../Plugins/VLC";
import { GetAsUrlParams } from "../../Helpers";
import { errorResponse, okResponse } from "../errors";

export async function fetchPlaylists(
    baseUrl: string,
    params: IBasicParams
): Promise<IBackendResponse<IPlaylist[]>> {
    const ret = await axios.get<{ "subsonic-response": IPlaylistsResponse }>(
        `${baseUrl}/rest/getPlaylists`,
        { params }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].playlists.playlist);
}

export async function fetchPlaylist(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IPlaylist>> {
    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/getPlaylist`,
        { params: { ...params, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].playlist);
}

export async function removeFromPlaylist(
    baseUrl: string,
    params: IBasicParams,
    id: string,
    track: number
): Promise<IBackendResponse<string>> {
    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/updatePlaylist`,
        {
            params: {
                ...params,
                songIndexToRemove: track,
                playlistId: id,
            },
        }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse("");
}

export async function createPlaylist(
    baseUrl: string,
    params: IBasicParams,
    songId: string[],
    name: string
): Promise<IBackendResponse<IPlaylist>> {
    const urlParams = GetAsUrlParams(params);
    urlParams.append("name", name);
    songId.forEach((s) => urlParams.append("songId", s));
    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/createPlaylist`,
        { params: urlParams }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret?.data["subsonic-response"].playlist);
}

export async function removePlaylist(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<string>> {
    const urlParams = GetAsUrlParams(params);
    urlParams.append("id", id);
    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/deletePlaylist`,
        { params: urlParams }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    return okResponse("");
}

export async function updatePlaylist(
    baseUrl: string,
    params: IBasicParams,
    playlist: IPlaylist
): Promise<IBackendResponse<IPlaylist>> {
    const urlParams = GetAsUrlParams(params);
    urlParams.append("name", playlist.name);
    urlParams.append("comment", playlist.comment ?? "");
    urlParams.append("public", playlist.public ? "true" : "false");
    urlParams.append("playlistId", playlist.id);

    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/updatePlaylist`,
        { params: urlParams }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);

    const createParams = GetAsUrlParams(params);
    createParams.append("playlistId", playlist.id);
    playlist.entry.forEach((s) => createParams.append("songId", s.id));
    const createRet = await axios.get<{
        "subsonic-response": IPlaylistResponse;
    }>(`${baseUrl}/rest/createPlaylist`, { params: createParams });
    if (createRet?.status !== 200) return errorResponse(ret?.statusText);
    return okResponse(ret?.data["subsonic-response"].playlist);
}

export async function addToPlaylist(
    baseUrl: string,
    params: IBasicParams,
    id: string,
    songId: string
): Promise<IBackendResponse<string>> {
    const ret = await axios.get<{ "subsonic-response": IPlaylistResponse }>(
        `${baseUrl}/rest/updatePlaylist`,
        {
            params: {
                ...params,
                songIdToAdd: songId,
                playlistId: id,
            },
        }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse("");
}
