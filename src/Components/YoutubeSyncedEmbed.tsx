import { useEffect, useRef } from "react";

declare global {
    interface Window {
        YT?: any;
        onYouTubeIframeAPIReady?: () => void;
    }
}

type Props = {
    videoId: string;
    playing: boolean;
    positionSec: number;
    visible: boolean;
    /** When true, YouTube provides audio (unmuted). */
    withAudio?: boolean;
    onEnded?: () => void;
};

let apiLoading = false;
const readyWaiters: Array<() => void> = [];

function ensureApi(): Promise<void> {
    if (window.YT?.Player) return Promise.resolve();
    return new Promise((resolve) => {
        readyWaiters.push(resolve);
        if (apiLoading) return;
        apiLoading = true;
        const prev = window.onYouTubeIframeAPIReady;
        window.onYouTubeIframeAPIReady = () => {
            prev?.();
            readyWaiters.splice(0).forEach((w) => w());
        };
        const tag = document.createElement("script");
        tag.src = "https://www.youtube.com/iframe_api";
        document.head.appendChild(tag);
    });
}

/** Official IFrame player; optionally unmuted for music-video audio. */
export default function YoutubeSyncedEmbed({
    videoId,
    playing,
    positionSec,
    visible,
    withAudio = true,
    onEnded,
}: Props) {
    const hostRef = useRef<HTMLDivElement>(null);
    const playerRef = useRef<any>(null);
    const lastSeek = useRef(-1);
    const onEndedRef = useRef(onEnded);
    onEndedRef.current = onEnded;

    useEffect(() => {
        let cancelled = false;
        const boot = async () => {
            await ensureApi();
            if (cancelled || !hostRef.current || !window.YT) return;
            hostRef.current.innerHTML = "";
            const mount = document.createElement("div");
            hostRef.current.appendChild(mount);
            playerRef.current = new window.YT.Player(mount, {
                width: "100%",
                height: "100%",
                videoId,
                playerVars: {
                    autoplay: 1,
                    controls: 0,
                    modestbranding: 1,
                    rel: 0,
                    playsinline: 1,
                },
                events: {
                    onReady: (e: any) => {
                        if (withAudio) {
                            e.target.unMute?.();
                            e.target.setVolume?.(100);
                        } else {
                            e.target.mute();
                        }
                        e.target.seekTo(positionSec, true);
                        if (playing) e.target.playVideo();
                        else e.target.pauseVideo();
                    },
                    onStateChange: (e: any) => {
                        // YT.PlayerState.ENDED === 0
                        if (e.data === 0) onEndedRef.current?.();
                    },
                },
            });
        };
        boot();
        return () => {
            cancelled = true;
            try {
                playerRef.current?.destroy?.();
            } catch {
                /* ignore */
            }
            playerRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [videoId, withAudio]);

    useEffect(() => {
        const p = playerRef.current;
        if (!p?.playVideo) return;
        if (playing) {
            if (withAudio) {
                p.unMute?.();
                p.setVolume?.(100);
            } else {
                p.mute?.();
            }
            p.playVideo();
        } else {
            p.pauseVideo();
        }
    }, [playing, withAudio]);

    useEffect(() => {
        const p = playerRef.current;
        if (!p?.seekTo || !withAudio) return;
        // With YouTube as audio master, only resync on large jumps (user seek).
        if (lastSeek.current >= 0 && Math.abs(positionSec - lastSeek.current) > 2) {
            p.seekTo(positionSec, true);
        }
        lastSeek.current = positionSec;
    }, [positionSec, withAudio]);

    if (!visible) return null;
    return (
        <div
            ref={hostRef}
            style={{ width: "100%", height: "100%", background: "#000" }}
        />
    );
}
