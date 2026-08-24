/// Playback track state is owned by the VLC Capacitor plugin listeners.
/// Components should subscribe via VLC.addListener rather than this context.
/// Kept for backwards-compatible empty defaults used in a few screens.
import { IAlbumSongResponse } from "./Models/API/Responses/IArtistResponse";

export const CurrentTrackContextDefValue: IAlbumSongResponse = {
    duration: 0,
    id: "",
    parent: "",
    title: "",
    track: 0,
    artist: "",
    coverArt: "",
    album: "",
    albumId: "",
};
