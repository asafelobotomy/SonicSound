import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import { IAccount, IAppContext } from "../Models/AppContext";
import { ISettings } from "../Plugins/VLC";
import {
    deleteAccountFromContext,
    loadStoredContext,
    performLogin,
    persistContext,
} from "./accounts";
import { errorResponse, okResponse } from "../subsonic/errors";
import * as lib from "../subsonic/endpoints/library";
import * as playlists from "../subsonic/endpoints/playlists";
import * as radio from "../subsonic/endpoints/radio";
import * as stars from "../subsonic/endpoints/stars";
import {
    clearSpotifyTokenCache,
    getSpotifyAccessToken,
    searchSpotifyArtist,
} from "../subsonic/spotify";
import type { Backend } from "./Backend";

/** Library / account / playlist API surface mixed onto Backend. */
export const backendApi = {
    async removeFromPlaylist(
        this: Backend,
        o: { id: string; track: number }
    ) {
        return playlists.removeFromPlaylist(
            this.url(),
            this.params(),
            o.id,
            o.track
        );
    },
    async createPlaylist(
        this: Backend,
        o: { songId: string[]; name: string }
    ) {
        return playlists.createPlaylist(
            this.url(),
            this.params(),
            o.songId,
            o.name
        );
    },
    async removePlaylist(this: Backend, o: { id: string }) {
        return playlists.removePlaylist(this.url(), this.params(), o.id);
    },
    async updatePlaylist(this: Backend, o: { playlist: IPlaylist }) {
        return playlists.updatePlaylist(this.url(), this.params(), o.playlist);
    },
    async addToPlaylist(
        this: Backend,
        o: { id: string | null; songId: string }
    ) {
        if (o.id === null) {
            const r = await this.createPlaylist({
                songId: [o.songId],
                name: "New playlist",
            });
            return r.status === "ok" ? okResponse("") : errorResponse(r.error);
        }
        return playlists.addToPlaylist(
            this.url(),
            this.params(),
            o.id,
            o.songId
        );
    },
    async downloadAlbum(this: Backend) {
        return errorResponse("Not supported on PWA");
    },
    async getCoverCacheSize(this: Backend) {
        return okResponse({ bytes: 0 });
    },
    async clearCoverCache(this: Backend) {
        return okResponse({ freedBytes: 0 });
    },
    async getOfflineMode(this: Backend) {
        return okResponse(false);
    },
    async setOfflineMode(this: Backend) {
        return okResponse(false);
    },
    async getSongStatus(this: Backend) {
        return okResponse(false);
    },
    async getSettings(this: Backend) {
        const s = localStorage.getItem("settings");
        return okResponse(
            s ? (JSON.parse(s) as ISettings) : { cacheSize: 0, transcoding: "" }
        );
    },
    async setSettings(this: Backend, options: ISettings) {
        localStorage.setItem("settings", JSON.stringify(options));
        return okResponse("");
    },
    /** Camera QR was removed; stubs keep IBackendPlugin satisfied for web. */
    getCameraPermission(this: Backend) {
        return Promise.resolve(errorResponse("Camera is not available on web"));
    },
    getCameraPermissionStatus(this: Backend) {
        return Promise.resolve(errorResponse("Camera is not available on web"));
    },
    async getCurrentPlaylist(this: Backend) {
        return okResponse(this.currentPlaylist);
    },
    getActiveAccount(this: Backend) {
        return Promise.resolve(okResponse(this.context.activeAccount));
    },
    async logout(this: Backend) {
        const cleared: IAccount = {
            username: null,
            password: "",
            url: "",
            type: "",
            usePlaintext: false,
        };
        this.context = { ...this.context, activeAccount: cleared };
        persistContext(this.context);
        try {
            await this.pause();
        } catch {
            /* ignore */
        }
        return okResponse(cleared);
    },
    deleteAccount(this: Backend, options: { url: string }) {
        this.context = deleteAccountFromContext(this.context, options.url);
        return Promise.resolve(okResponse(""));
    },
    getAccounts(this: Backend) {
        return Promise.resolve(okResponse(this.context.accounts));
    },
    async getTopAlbums(
        this: Backend,
        o: { type: string | null; size: number | null }
    ) {
        return lib.fetchTopAlbums(this.url(), this.params(), o.type, o.size);
    },
    async getRandomSongs(this: Backend) {
        return lib.fetchRandomSongs(this.url(), this.params());
    },
    async getPlaylists(this: Backend) {
        return playlists.fetchPlaylists(this.url(), this.params());
    },
    async getInternetRadioStations(this: Backend) {
        return lib.fetchInternetRadioStations(this.url(), this.params());
    },
    async createInternetRadioStation(
        this: Backend,
        o: { name: string; streamUrl: string; homePageUrl?: string }
    ) {
        return radio.createInternetRadioStation(this.url(), this.params(), o);
    },
    async updateInternetRadioStation(
        this: Backend,
        o: { id: string; name: string; streamUrl: string; homePageUrl?: string }
    ) {
        return radio.updateInternetRadioStation(this.url(), this.params(), o);
    },
    async deleteInternetRadioStation(this: Backend, o: { id: string }) {
        return radio.deleteInternetRadioStation(this.url(), this.params(), o.id);
    },
    async discoverServers(this: Backend) {
        return errorResponse<string[]>(
            "LAN discovery is only available on Android TV"
        );
    },
    async getPlaylist(this: Backend, o: { id: string }) {
        return playlists.fetchPlaylist(this.url(), this.params(), o.id);
    },
    getAlbumArt(this: Backend, o: { id: string }) {
        return Promise.resolve(
            okResponse(lib.coverArtUrl(this.url(), this.params(), o.id))
        );
    },
    getSongArt(this: Backend) {
        return Promise.resolve(okResponse(""));
    },
    async loadContext(this: Backend) {
        this.context = loadStoredContext();
    },
    async getSpotifyToken(this: Backend) {
        try {
            return await getSpotifyAccessToken();
        } catch {
            clearSpotifyTokenCache();
            throw new Error("Spotify is not configured");
        }
    },
    async login(
        this: Backend,
        options: {
            username: string;
            password: string;
            url: string;
            usePlaintext: boolean;
        }
    ) {
        const result = await performLogin({
            ...options,
            existing: this.context,
        });
        if (result.context) this.context = result.context;
        return result;
    },
    getContext(this: Backend) {
        return Promise.resolve(okResponse(this.context));
    },
    setContext(this: Backend, options: { context: IAppContext }) {
        this.context = options.context;
        persistContext(options.context);
        return Promise.resolve(okResponse(""));
    },
    async getSong(this: Backend, o: { id: string }) {
        return lib.fetchSong(this.url(), this.params(), o.id);
    },
    async getSimilarSongs(this: Backend, o: { id: string }) {
        return lib.fetchSimilarSongs(this.url(), this.params(), o.id);
    },
    async getArtists(this: Backend) {
        return lib.fetchArtists(this.url(), this.params());
    },
    async search(this: Backend, o: { query: string }) {
        return lib.fetchSearch(this.url(), this.params(), o.query);
    },
    async getArtist(this: Backend, o: { id: string }) {
        return lib.fetchArtist(this.url(), this.params(), o.id);
    },
    async getAlbums(this: Backend) {
        return lib.fetchAlbums(this.url(), this.params());
    },
    async getAlbum(this: Backend, o: { id: string }) {
        return lib.fetchAlbum(this.url(), this.params(), o.id);
    },
    async getArtistInfo(this: Backend, o: { id: string }) {
        return lib.fetchArtistInfo(this.url(), this.params(), o.id);
    },
    async getLyrics(this: Backend, o: { artist: string; title: string }) {
        return lib.fetchLyrics(
            this.url(),
            this.params(),
            o.artist,
            o.title
        );
    },
    async getArtistArt(this: Backend, o: { id: string }) {
        const artist = await this.getArtist({ id: o.id });
        if (artist.status !== "ok" || !artist.value) return okResponse("");
        const cover = artist.value.coverArt;
        if (cover?.startsWith("http")) return okResponse(cover);
        if (cover) {
            return okResponse(lib.coverArtUrl(this.url(), this.params(), cover));
        }
        const fromId = lib.coverArtUrl(this.url(), this.params(), o.id);
        const info = await this.getArtistInfo({ id: o.id });
        if (info.status === "ok" && info.value?.largeImageUrl) {
            return okResponse(info.value.largeImageUrl);
        }
        try {
            const items = await searchSpotifyArtist(
                await this.getSpotifyToken(),
                artist.value.name
            );
            if (items.length > 0 && items[0].name === artist.value.name) {
                const img = items[0].images[1] ?? items[0].images[0];
                if (img?.url) return okResponse(img.url);
            }
        } catch {
            /* optional */
        }
        return okResponse(fromId);
    },
    async star(this: Backend, o: { id: string }) {
        return stars.starSong(this.url(), this.params(), o.id);
    },
    async unstar(this: Backend, o: { id: string }) {
        return stars.unstarSong(this.url(), this.params(), o.id);
    },
};
