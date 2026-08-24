import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";

export function emptyTrack(): IAlbumSongResponse {
    return {
        album: "",
        albumId: "",
        artist: "",
        coverArt: "",
        duration: 0,
        id: "",
        parent: "",
        title: "",
        track: 0,
    };
}

export function emptyPlaylist(): IPlaylist {
    return {
        comment: "",
        coverArt: "",
        created: "",
        duration: 0,
        entry: [],
        id: "",
        name: "",
        owner: "",
        public: false,
        songCount: 0,
    };
}

export function shuffleInPlace<T>(array: T[]): T[] {
    let currentIndex = array.length;
    while (currentIndex !== 0) {
        const randomIndex = Math.floor(Math.random() * currentIndex);
        currentIndex--;
        [array[currentIndex], array[randomIndex]] = [
            array[randomIndex],
            array[currentIndex],
        ];
    }
    return array;
}

/** Prefer explicit coverArt id; fall back to albumId for older payloads. */
export function resolveCoverArtId(track: {
    coverArt?: string;
    albumId?: string;
}): string {
    return track.coverArt || track.albumId || "";
}
