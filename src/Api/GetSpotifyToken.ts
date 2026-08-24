import axios from "axios";

export interface ISpotifyToken {
    token: string;
}

/**
 * Fetches a Spotify client-credentials token when VITE_SPOTIFY_* are set.
 * Core playback does not require Spotify.
 *
 * Production web builds must not embed a client secret. Spotify similarity
 * on native Android uses BuildConfig secrets instead of this module.
 */
export default async function GetSpotifyToken(): Promise<string> {
    if (import.meta.env.PROD) {
        throw new Error(
            "Spotify client credentials are disabled in production web builds."
        );
    }

    const client_id = import.meta.env.VITE_SPOTIFY_CLIENT_ID || "";
    const client_secret = import.meta.env.VITE_SPOTIFY_CLIENT_SECRET || "";

    if (!client_id || !client_secret) {
        throw new Error(
            "Spotify is not configured. Set VITE_SPOTIFY_CLIENT_ID and VITE_SPOTIFY_CLIENT_SECRET to enable similarity features in development."
        );
    }

    const body = new URLSearchParams({ grant_type: "client_credentials" });

    const response = await axios.post(
        "https://accounts.spotify.com/api/token",
        body.toString(),
        {
            headers: {
                Accept: "application/json",
                "Content-Type": "application/x-www-form-urlencoded",
            },
            auth: {
                username: client_id,
                password: client_secret,
            },
        }
    );
    return response.data.access_token;
}
