import GetSpotifyToken from "../Api/GetSpotifyToken";
import axios from "axios";
import {
    ISpotifyArtistItem,
    ISpotifyArtistsSearch,
} from "../Models/API/Responses/ISpotifyResponse";

let cachedToken = "";

export async function getSpotifyAccessToken(): Promise<string> {
    if (cachedToken) return cachedToken;
    cachedToken = await GetSpotifyToken();
    return cachedToken;
}

export function clearSpotifyTokenCache(): void {
    cachedToken = "";
}

export async function searchSpotifyArtist(
    token: string,
    query: string
): Promise<ISpotifyArtistItem[]> {
    try {
        const response = await axios.get<ISpotifyArtistsSearch>(
            "https://api.spotify.com/v1/search",
            {
                headers: {
                    Accept: "application/json",
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                params: { q: query, type: "artist" },
            }
        );
        return response.data.artists.items;
    } catch {
        return [];
    }
}
