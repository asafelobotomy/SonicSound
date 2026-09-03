import type { Backend } from "./Backend";
import { errorResponse, okResponse } from "../subsonic/errors";
import type { IBackendResponse } from "../Plugins/VLC";

function isValidIp(ip: string): boolean {
    return /^(25[0-5]|2[0-4]\d|[01]?\d\d?)(\.(25[0-5]|2[0-4]\d|[01]?\d\d?)){3}$/.test(
        ip
    );
}

/** Web/PWA stubs + QR login helpers mixed onto Backend. */
export const backendRemote = {
    sendUdpBroadcast(this: Backend) {
        return Promise.resolve(errorResponse("UDP discovery requires the Android app"));
    },
    async getWebsocketStatus(this: Backend) {
        return okResponse(false);
    },
    async disconnectWebsocket(this: Backend) {
        return okResponse("");
    },
    async qrLogin(this: Backend, options: { ip: string; mode?: "remote" | "login" }) {
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
    },
    async connectRemote(this: Backend) {
        return errorResponse("Remote is only available on Android");
    },
    async startRemoteDiscovery(this: Backend) {
        return errorResponse("Remote is only available on Android");
    },
    async stopRemoteDiscovery(this: Backend) {
        return okResponse("");
    },
    async getDiscoveredRemotes(this: Backend) {
        return okResponse([]);
    },
    async playJukeboxCollection(this: Backend, _o: { collection: string; remote?: boolean }) {
        return errorResponse("Jukebox collections require the Android app");
    },
    async getGenres(
        this: Backend
    ): Promise<IBackendResponse<{ value: string; songCount?: number }[]>> {
        return errorResponse("Not implemented on web");
    },
    async getServerCapabilities(this: Backend) {
        return okResponse({
            playbackReport: false,
            sonicSimilarity: false,
            playQueue: false,
        });
    },
};
