import axios from "axios";
import { WebPlugin } from "@capacitor/core";
import { GetAsParams, getPlaylistDuration } from "../Helpers";
import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import { IAppContext } from "../Models/AppContext";
import {
    IBackendPlugin,
    IBackendResponse,
    ICurrentState,
} from "../Plugins/VLC";
import { basicParamsFor, loadStoredContext } from "./accounts";
import { backendApi } from "./backendApi";
import type { BackendApiMethods } from "./backendApiMethods";
import {
    emptyPlaylist,
    emptyTrack,
    resolveCoverArtId,
    shuffleInPlace,
} from "./queue";
import { errorResponse, okResponse } from "../subsonic/errors";
import * as lib from "../subsonic/endpoints/library";
import * as playlists from "../subsonic/endpoints/playlists";
import { ISettings } from "../Plugins/VLC";

function isValidIp(ip: string): boolean {
    return /^(25[0-5]|2[0-4]\d|[01]?\d\d?)(\.(25[0-5]|2[0-4]\d|[01]?\d\d?)){3}$/.test(
        ip
    );
}

/**
 * Web/PWA Capacitor backend: HTMLAudioElement playback + Subsonic REST.
 */
export interface Backend extends BackendApiMethods {}
export class Backend extends WebPlugin implements IBackendPlugin {
    currentPlaylist: IPlaylist = emptyPlaylist();
    originalCurrentPlaylist: IAlbumSongResponse[] = [];
    isPlaying = false;
    audio = new Audio();
    context: IAppContext = loadStoredContext();
    currentTrack: IAlbumSongResponse = emptyTrack();
    isShuffle = false;
    repeatMode: "off" | "all" | "one" = "off";

    constructor() {
        super();
        Object.assign(this, backendApi);
        this.wireAudioElement();
    }

    params() {
        return basicParamsFor(this.context);
    }
    url() {
        return this.context.activeAccount.url;
    }

    private wireAudioElement() {
        if ("mediaSession" in navigator) {
            navigator.mediaSession.setActionHandler("pause", () => this.pause());
            navigator.mediaSession.setActionHandler("play", () => this.play());
            navigator.mediaSession.setActionHandler("nexttrack", () =>
                this._next()
            );
            navigator.mediaSession.setActionHandler("previoustrack", () =>
                this.prev()
            );
        }
        this.audio.onplay = () => {
            this.isPlaying = true;
            if ("mediaSession" in navigator) {
                navigator.mediaSession.playbackState = "playing";
            }
            this.notifyListeners("play", null);
        };
        this.audio.onpause = () => {
            this.isPlaying = false;
            if ("mediaSession" in navigator) {
                navigator.mediaSession.playbackState = "paused";
            }
            this.notifyListeners("paused", null);
        };
        this.audio.ontimeupdate = (ev: Event) => {
            const target = ev.target as HTMLAudioElement;
            if ("mediaSession" in navigator && this.currentTrack.duration) {
                navigator.mediaSession.setPositionState({
                    duration: this.currentTrack.duration,
                    playbackRate: 1,
                    position: Math.min(
                        target.currentTime,
                        this.currentTrack.duration
                    ),
                });
            }
            this.notifyListeners("progress", {
                time: target.currentTime / (this.currentTrack.duration || 1),
            });
        };
        this.audio.onended = () => {
            this.isPlaying = false;
            if ("mediaSession" in navigator) {
                navigator.mediaSession.playbackState = "none";
            }
            this.notifyListeners("stopped", null);
            this._next();
        };
    }

    shufflePlaylist(): Promise<IBackendResponse<string>> {
        if (!this.isShuffle) {
            this.currentPlaylist.entry = shuffleInPlace([
                ...this.currentPlaylist.entry,
            ]);
            const idx = this.currentPlaylist.entry.indexOf(this.currentTrack);
            if (idx > 0) {
                [
                    this.currentPlaylist.entry[0],
                    this.currentPlaylist.entry[idx],
                ] = [
                    this.currentPlaylist.entry[idx],
                    this.currentPlaylist.entry[0],
                ];
            }
        } else {
            this.currentPlaylist.entry = [...this.originalCurrentPlaylist];
        }
        this.isShuffle = !this.isShuffle;
        this.notifyListeners("playlistUpdated", null);
        return Promise.resolve(okResponse(""));
    }

    cycleRepeat(): Promise<IBackendResponse<string>> {
        this.repeatMode =
            this.repeatMode === "off"
                ? "all"
                : this.repeatMode === "all"
                  ? "one"
                  : "off";
        this.notifyListeners("playlistUpdated", null);
        return Promise.resolve(okResponse(this.repeatMode));
    }

    sendUdpBroadcast() {
        return Promise.reject(new Error("Method not implemented."));
    }
    async getWebsocketStatus() {
        return okResponse(false);
    }
    async disconnectWebsocket() {
        return okResponse("");
    }
    async qrLogin(options: { ip: string; mode?: "remote" | "login" }) {
        if (!isValidIp(options.ip)) {
            return errorResponse(
                "The QR code is not an IP address. Please try again."
            );
        }
        try {
            const socket = new WebSocket(`ws://${options.ip}:30001`);
            socket.onopen = () => {
                socket.send(
                    JSON.stringify({
                        type: "login",
                        data: this.context.activeAccount,
                    })
                );
            };
            socket.onerror = () => {
                this.notifyListeners(
                    "EX",
                    "There was an error connecting to the TV. Please, try again"
                );
            };
            socket.onmessage = (message) => {
                if (message.data === "sonicsound") socket.close();
            };
            return okResponse("Login request sent");
        } catch (e: unknown) {
            return errorResponse((e as Error).message);
        }
    }

    getCurrentState(): Promise<IBackendResponse<ICurrentState>> {
        return Promise.resolve(
            okResponse({
                playing: this.isPlaying,
                currentTrack: this.currentTrack,
                playtime: this.audio.currentTime / (this.audio.duration || 1),
                shuffling: this.isShuffle,
                repeatMode: this.repeatMode,
            })
        );
    }

    scrobble() {
        axios.get(`${this.url()}/rest/scrobble`, {
            params: { ...this.params(), id: this.currentTrack.id },
        });
    }

    private snapshotPlaylist(playlist: IPlaylist, track: number) {
        this.currentPlaylist = playlist;
        this.originalCurrentPlaylist = [...playlist.entry];
        this.currentTrack = playlist.entry[track];
        this._playCurrent();
    }

    async playAlbum(o: { album: string; track: number }) {
        const album = await lib.fetchAlbum(this.url(), this.params(), o.album);
        if (album.status !== "ok" || !album.value) {
            return errorResponse(album.error);
        }
        const owner = (await this.getActiveAccount()).value!.username!;
        this.snapshotPlaylist(
            {
                comment: `by ${album.value.artist}`,
                coverArt: album.value.coverArt,
                created: "",
                duration: getPlaylistDuration(album.value.song),
                entry: album.value.song,
                id: "current",
                name: album.value.name!,
                owner,
                public: false,
                songCount: album.value.song.length,
            },
            o.track
        );
        return okResponse("");
    }

    async playPlaylist(o: { playlist: string; track: number }) {
        const playlist = await playlists.fetchPlaylist(
            this.url(),
            this.params(),
            o.playlist
        );
        if (playlist.status !== "ok" || !playlist.value) {
            return errorResponse(playlist.error);
        }
        this.snapshotPlaylist(playlist.value, o.track);
        return okResponse("");
    }

    async skipTo(o: { track: number }) {
        if (o.track >= this.currentPlaylist.entry.length) {
            return errorResponse("The track does not exist on the playlist");
        }
        this.currentTrack = this.currentPlaylist.entry[o.track];
        this._playCurrent();
        return okResponse("");
    }

    async playRadio(o: { song: string }) {
        const songList = await this.getSimilarSongs({ id: o.song });
        if (songList.status !== "ok") return errorResponse(songList.error);
        const song = await this.getSong({ id: o.song });
        if (song.status !== "ok" || !song.value) {
            return errorResponse(song.error);
        }
        const entry = [song.value, ...songList.value!];
        const owner = (await this.getActiveAccount()).value!.username!;
        this.snapshotPlaylist(
            {
                comment: `by ${song.value.artist}`,
                coverArt: resolveCoverArtId(song.value),
                created: "",
                duration: getPlaylistDuration(entry),
                entry,
                id: "current",
                name: `Radio based on ${song.value.title}`,
                owner,
                public: false,
                songCount: entry.length,
            },
            0
        );
        return okResponse("");
    }

    async playInternetRadio(o: { streamUrl: string; name: string }) {
        const track: IAlbumSongResponse = {
            ...emptyTrack(),
            id: `radio:${o.name}`,
            title: o.name,
            artist: "Internet Radio",
            album: o.name,
            duration: 0,
        };
        this.currentTrack = track;
        this.currentPlaylist = {
            ...emptyPlaylist(),
            id: "current",
            name: o.name,
            comment: "Internet Radio",
            entry: [track],
            songCount: 1,
        };
        this.audio.src = o.streamUrl;
        await this.audio.play();
        this.isPlaying = true;
        this.notifyListeners("play", null);
        return okResponse("");
    }

    async play() {
        if (this.currentTrack && !this.isPlaying) await this.audio.play();
        return okResponse("");
    }
    async pause() {
        if (this.isPlaying) await this.audio.pause();
        return okResponse("");
    }

    private songParams(track: IAlbumSongResponse) {
        const raw = localStorage.getItem("settings");
        const settings: ISettings = raw
            ? JSON.parse(raw)
            : { cacheSize: 0, transcoding: "" };
        const transcoding = settings.transcoding || "raw";
        return GetAsParams({
            ...this.params(),
            id: track.id,
            format: transcoding,
            estimateContentLength: settings.transcoding ? "true" : "false",
        });
    }

    async _playCurrent() {
        if ("mediaSession" in navigator) {
            const coverId = resolveCoverArtId(this.currentTrack);
            const art = coverId
                ? lib.coverArtUrl(this.url(), this.params(), coverId)
                : "";
            navigator.mediaSession.metadata = new MediaMetadata({
                title: this.currentTrack.title,
                artist: this.currentTrack.artist,
                album: this.currentTrack.album,
                artwork: art ? [{ src: art, sizes: "any" }] : [],
            });
        }
        await this.notifyListeners("currentTrack", {
            currentTrack: this.currentTrack,
        });
        this.scrobble();
        this.audio.src = `${this.url()}/rest/stream?${this.songParams(
            this.currentTrack
        )}`;
        await this.audio.play();
    }

    _prev() {
        const i = this.currentPlaylist.entry.indexOf(this.currentTrack);
        if (i > 0) {
            this.currentTrack = this.currentPlaylist.entry[i - 1];
            this._playCurrent();
        }
    }
    async prev() {
        this._prev();
        return okResponse("");
    }
    _next() {
        const entries = this.currentPlaylist.entry;
        const i = entries.indexOf(this.currentTrack);
        if (i >= 0 && i < entries.length - 1) {
            this.currentTrack = entries[i + 1];
            this._playCurrent();
            return;
        }
        if (this.repeatMode === "one") {
            this.audio.currentTime = 0;
            void this._playCurrent();
            return;
        }
        if (this.repeatMode === "all" && entries.length > 0) {
            this.currentTrack = entries[0];
            this._playCurrent();
        }
    }
    async next() {
        this._next();
        return okResponse("");
    }
    setVolume(o: { volume: number }) {
        this.audio.volume = o.volume;
        return Promise.resolve(okResponse(""));
    }
    seek(o: { time: number }) {
        this.audio.currentTime = o.time * this.currentTrack.duration;
        return Promise.resolve(okResponse(""));
    }
    removeAllListeners() {
        return super.removeAllListeners();
    }

    async connectRemote() {
        return errorResponse("Remote is only available on Android");
    }
    async startRemoteDiscovery() {
        return errorResponse("Remote is only available on Android");
    }
    async stopRemoteDiscovery() {
        return okResponse("");
    }
    async getDiscoveredRemotes() {
        return okResponse([]);
    }
    async playJukeboxCollection(_o: { collection: string; remote?: boolean }) {
        return errorResponse("Jukebox collections require the Android app");
    }
    async getGenres(): Promise<
        IBackendResponse<{ value: string; songCount?: number }[]>
    > {
        return errorResponse("Not implemented on web");
    }
    async getServerCapabilities(): Promise<
        IBackendResponse<{
            playbackReport: boolean;
            sonicSimilarity: boolean;
            playQueue: boolean;
        }>
    > {
        return okResponse({
            playbackReport: false,
            sonicSimilarity: false,
            playQueue: false,
        });
    }
}
