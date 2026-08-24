import axios from "axios";
import { IBasicParams } from "../../Models/API/Requests/BasicParams";
import { IAlbumsResponse } from "../../Models/API/Responses/IAlbumsResponse";
import { IArtist } from "../../Models/API/Responses/IArtist";
import {
    IArtistInfo,
    IArtistInfoResponse,
    ISearchResponse,
    ISearchResult,
} from "../../Models/API/Responses/IArtistInfoResponse";
import {
    IAlbumArtistResponse,
    IAlbumResponse,
    IAlbumSongResponse,
    IArtistResponse,
    IInnerAlbumResponse,
    IInnerArtistResponse,
    IRandomSongsResponse,
    ISimilarSongsResponse,
    ISongResponse,
} from "../../Models/API/Responses/IArtistResponse";
import { IArtistsResponse } from "../../Models/API/Responses/IArtistsResponse";
import { IBackendResponse } from "../../Plugins/VLC";
import { errorResponse, okResponse } from "../errors";
import { GetAsParams } from "../../Helpers";

export type ParamsFn = () => IBasicParams;
export type BaseUrlFn = () => string;

export async function fetchArtists(
    baseUrl: string,
    params: IBasicParams
): Promise<IBackendResponse<IArtist[]>> {
    const ret = await axios.get<{ "subsonic-response": IArtistsResponse }>(
        `${baseUrl}/rest/getArtists`,
        { params }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    const r = ret.data["subsonic-response"].artists!.index!.reduce<IArtist[]>(
        (previous, s) => [...previous, ...s.artist],
        []
    );
    return okResponse(r);
}

export async function fetchSearch(
    baseUrl: string,
    params: IBasicParams,
    query: string
): Promise<IBackendResponse<ISearchResult>> {
    const ret = await axios.get<{ "subsonic-response": ISearchResponse }>(
        `${baseUrl}/rest/search3`,
        { params: { ...params, query } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].searchResult3);
}

export async function fetchArtist(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IInnerArtistResponse>> {
    const ret = await axios.get<{ "subsonic-response": IArtistResponse }>(
        `${baseUrl}/rest/getArtist`,
        { params: { ...params, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].artist);
}

export async function fetchAlbums(
    baseUrl: string,
    params: IBasicParams
): Promise<IBackendResponse<IAlbumArtistResponse[]>> {
    let more = true;
    let albumsResponse: IAlbumsResponse | null = null;
    let page = 0;
    while (more) {
        const ret = await axios.get<{ "subsonic-response": IAlbumsResponse }>(
            `${baseUrl}/rest/getAlbumList2`,
            {
                params: {
                    ...params,
                    type: "alphabeticalByName",
                    size: 500,
                    offset: page * 500,
                },
            }
        );
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(
                ret?.data["subsonic-response"]?.error?.message
            );
        }
        if (albumsResponse === null) {
            albumsResponse = ret.data["subsonic-response"];
        } else {
            albumsResponse.albumList2.album = [
                ...albumsResponse.albumList2.album,
                ...ret.data["subsonic-response"].albumList2.album,
            ];
        }
        page++;
        if (ret.data["subsonic-response"].albumList2.album.length < 500) {
            more = false;
        }
    }
    return okResponse(albumsResponse!.albumList2.album);
}

export async function fetchAlbum(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IInnerAlbumResponse>> {
    const ret = await axios.get<{ "subsonic-response": IAlbumResponse }>(
        `${baseUrl}/rest/getAlbum`,
        { params: { ...params, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].album);
}

export async function fetchTopAlbums(
    baseUrl: string,
    params: IBasicParams,
    type: string | null,
    size: number | null
): Promise<IBackendResponse<IAlbumArtistResponse[]>> {
    const ret = await axios.get<{ "subsonic-response": IAlbumsResponse }>(
        `${baseUrl}/rest/getAlbumList2`,
        {
            params: {
                ...params,
                type: type ?? "frequent",
                size: size ?? 10,
            },
        }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].albumList2.album);
}

export async function fetchRandomSongs(
    baseUrl: string,
    params: IBasicParams
): Promise<IBackendResponse<IAlbumSongResponse[]>> {
    const ret = await axios.get<{ "subsonic-response": IRandomSongsResponse }>(
        `${baseUrl}/rest/getRandomSongs`,
        { params: { ...params, size: 10 } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].randomSongs.song);
}

export async function fetchSong(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IAlbumSongResponse>> {
    const ret = await axios.get<{ "subsonic-response": ISongResponse }>(
        `${baseUrl}/rest/getSong`,
        { params: { ...params, size: 10, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].song);
}

export async function fetchSimilarSongs(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IAlbumSongResponse[]>> {
    const ret = await axios.get<{ "subsonic-response": ISimilarSongsResponse }>(
        `${baseUrl}/rest/getSimilarSongs2`,
        { params: { ...params, size: 10, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].similarSongs2.song);
}

export async function fetchArtistInfo(
    baseUrl: string,
    params: IBasicParams,
    id: string
): Promise<IBackendResponse<IArtistInfo>> {
    const ret = await axios.get<{ "subsonic-response": IArtistInfoResponse }>(
        `${baseUrl}/rest/getArtistInfo2`,
        { params: { ...params, id } }
    );
    if (ret?.status !== 200) return errorResponse(ret?.statusText);
    if (ret?.data["subsonic-response"]?.status !== "ok") {
        return errorResponse(ret?.data["subsonic-response"]?.error?.message);
    }
    return okResponse(ret.data["subsonic-response"].artistInfo2);
}

export function coverArtUrl(
    baseUrl: string,
    params: IBasicParams,
    coverArtId: string
): string {
    return `${baseUrl}/rest/getCoverArt?${GetAsParams({
        ...params,
        id: coverArtId,
    })}`;
}

export async function fetchLyrics(
    baseUrl: string,
    params: IBasicParams,
    artist: string,
    title: string
): Promise<IBackendResponse<string>> {
    try {
        const ret = await axios.get<{
            "subsonic-response": {
                status: string;
                lyrics?: { value?: string };
                error?: { message?: string };
            };
        }>(`${baseUrl}/rest/getLyrics`, {
            params: { ...params, artist, title },
        });
        if (ret?.status !== 200) return errorResponse(ret?.statusText);
        if (ret?.data["subsonic-response"]?.status !== "ok") {
            return errorResponse(
                ret?.data["subsonic-response"]?.error?.message
            );
        }
        return okResponse(ret.data["subsonic-response"].lyrics?.value ?? "");
    } catch (e: unknown) {
        const err = e as { message?: string };
        return errorResponse(err?.message ?? "Lyrics unavailable");
    }
}
