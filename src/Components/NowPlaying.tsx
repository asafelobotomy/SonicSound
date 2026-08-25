import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import { CurrentTrackContextDefValue } from "../AudioContext";
import { SecondsToHHSS } from "../Helpers";
import "./NowPlaying.scss";
import VLC from "../Plugins/VLC";
import {
    FocusContext,
    useFocusable,
} from "@noriginmedia/norigin-spatial-navigation";
import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { PluginListenerHandle } from "@capacitor/core";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import { PlaylistEntry } from "./PlaylistEntry";
import TVActionButton from "./TVActionButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
    faBackward,
    faBackwardStep,
    faFilm,
    faForward,
    faForwardStep,
    faHeart,
    faPause,
    faPlay,
} from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartOutline } from "@fortawesome/free-regular-svg-icons";
import { Toast } from "@capacitor/toast";
import YoutubeSyncedEmbed from "./YoutubeSyncedEmbed";
import { searchMusicVideo } from "../youtube/match";
import { validAccessToken } from "../youtube/oauth";

export default function NowPlaying() {
    const [currentTrack, setCurrentTrack] = useState<IAlbumSongResponse>(
        CurrentTrackContextDefValue
    );
    const [playing, setPlaying] = useState<boolean>(false);
    const [playtime, setPlaytime] = useState<number>(0);
    const [coverArt, setCoverArt] = useState<string>("");
    const [playlist, setPlaylist] = useState<IPlaylist>();
    const [videoId, setVideoId] = useState<string | null>(null);
    const [mvMode, setMvMode] = useState(false);
    const [videoActive, setVideoActive] = useState(false);
    const [liked, setLiked] = useState(false);
    const listeners = useRef<PluginListenerHandle[]>([]);
    const mvModeRef = useRef(false);
    mvModeRef.current = mvMode;

    const changePlayTime = useCallback(
        (e: ChangeEvent<HTMLInputElement>): void => {
            const time = parseFloat(e.target.value);
            VLC.seek({ time: time });
        },
        []
    );

    const { ref, focusKey } = useFocusable();

    const applyServerAudio = useCallback(async (on: boolean) => {
        await VLC.setVolume({ volume: on ? 100 : 0 });
    }, []);

    const loadMusicVideoForTrack = useCallback(
        async (track: IAlbumSongResponse) => {
            const settings = await VLC.getSettings();
            const s = settings.value;
            const enabled = s?.youtubeVideosEnabled ?? false;
            const allowAny = s?.youtubeAllowAnyChannel ?? false;
            const token = s ? await validAccessToken(s) : "";
            const key = s?.youtubeApiKey ?? "";
            if (!enabled || (!token && !key)) {
                Toast.show({
                    text: "Enable Videos and sign in with YouTube in Settings.",
                });
                setMvMode(false);
                setVideoActive(false);
                setVideoId(null);
                await applyServerAudio(true);
                return;
            }
            const match = await searchMusicVideo(
                { accessToken: token, apiKey: key },
                track.artist,
                track.title,
                allowAny
            );
            if (!mvModeRef.current) return;
            if (!match) {
                setVideoId(null);
                setVideoActive(false);
                await applyServerAudio(true);
                await VLC.play();
                setPlaying(true);
                return;
            }
            setVideoId(match.id);
            setVideoActive(true);
            setPlaying(true);
            await applyServerAudio(false);
            await VLC.pause();
        },
        [applyServerAudio]
    );

    useEffect(() => {
        const fetch = async () => {
            const coverId = currentTrack.coverArt || currentTrack.albumId;
            if (coverId) {
                setCoverArt((await VLC.getAlbumArt({ id: coverId })).value!);
            }
            setPlaylist((await VLC.getCurrentPlaylist()).value!!);
            setLiked(!!currentTrack.starred);
            if (mvModeRef.current && currentTrack.id) {
                await applyServerAudio(false);
                await VLC.pause();
                await loadMusicVideoForTrack(currentTrack);
            }
        };
        fetch();
    }, [currentTrack, loadMusicVideoForTrack, applyServerAudio]);

    const toggleLike = useCallback(async () => {
        if (!currentTrack.id) return;
        const next = !liked;
        const ret = next
            ? await VLC.star({ id: currentTrack.id })
            : await VLC.unstar({ id: currentTrack.id });
        if (ret.status !== "ok") {
            Toast.show({ text: ret.error || "Could not update like" });
            return;
        }
        setLiked(next);
        setCurrentTrack({
            ...currentTrack,
            starred: next ? "now" : undefined,
        });
    }, [currentTrack, liked]);

    useEffect(() => {
        const get = async () => {
            const current = await VLC.getCurrentState();
            if (current.status === "ok") {
                setCurrentTrack(current.value?.currentTrack!);
                setPlaying(current.value?.playing!);
                setPlaytime(current.value?.playtime!);
            }
            setPlaylist((await VLC.getCurrentPlaylist()).value!!);
        };
        setTimeout(() => get(), 500);
    }, []);

    const toggleMusicVideo = async () => {
        if (mvMode) {
            setMvMode(false);
            setVideoActive(false);
            setVideoId(null);
            await applyServerAudio(true);
            await VLC.play();
            return;
        }
        setMvMode(true);
        await loadMusicVideoForTrack(currentTrack);
    };

    const onVideoEnded = useCallback(() => {
        if (!mvModeRef.current) return;
        VLC.next();
    }, []);

    const playNext = useCallback(() => {
        VLC.next();
    }, []);

    const playPrev = useCallback(() => {
        VLC.prev();
    }, []);

    const seekForward = useCallback(() => {
        VLC.seek({
            time: Math.min(playtime + (1 / currentTrack.duration) * 10, 1),
        });
    }, [currentTrack, playtime]);
    const seekBackward = useCallback(() => {
        VLC.seek({
            time: Math.max(playtime - (1 / currentTrack.duration) * 10, 0),
        });
    }, [currentTrack, playtime]);

    const togglePlaying = async () => {
        if (mvMode && videoActive) {
            if (playing) {
                setPlaying(false);
                await VLC.pause();
            } else {
                setPlaying(true);
                await applyServerAudio(false);
                await VLC.pause();
            }
            return;
        }
        if (playing) {
            VLC.pause();
        } else {
            VLC.play();
        }
    };

    useEffect(() => {
        const aw = async () => {
            listeners.current.forEach(async (listener) => {
                await listener.remove();
            });
            listeners.current = [
                await VLC.addListener("play", () => {
                    if (!(mvModeRef.current && videoActive)) setPlaying(true);
                }),
                await VLC.addListener("paused", () => {
                    if (!(mvModeRef.current && videoActive)) setPlaying(false);
                }),
                await VLC.addListener("stopped", () => setPlaying(false)),
                await VLC.addListener("currentTrack", (info: any) => {
                    setCurrentTrack(info.currentTrack);
                }),
                await VLC.addListener("progress", (info: any) => {
                    if (!(mvModeRef.current && videoActive)) {
                        setPlaytime(info.time);
                    }
                }),
            ];
        };
        aw();
    }, [videoActive]);

    const positionSec = (playtime ?? 0) * (currentTrack?.duration ?? 0);
    const showVideo = mvMode && videoActive && !!videoId;

    return (
        <div className="now-playing-tv d-flex flex-column align-items-center justify-content-between">
            <div className="now-playing-main d-flex flex-row justify-content-between align-items-stretch">
                <div className="now-playing-media-col d-flex flex-column align-items-center justify-content-center">
                    <div
                        className="current-track-img-tv"
                        style={{ position: "relative", overflow: "hidden" }}
                    >
                        {!showVideo && (
                            <img alt="" className="w-100 h-100" src={coverArt} />
                        )}
                        {showVideo && videoId && (
                            <YoutubeSyncedEmbed
                                videoId={videoId}
                                playing={playing}
                                positionSec={0}
                                visible={showVideo}
                                withAudio={true}
                                onEnded={onVideoEnded}
                            />
                        )}
                    </div>
                    <div className="d-flex flex-row align-items-center gap-2 mt-2">
                        <button
                            type="button"
                            className="btn btn-outline-light btn-sm"
                            onClick={toggleMusicVideo}
                        >
                            <FontAwesomeIcon icon={faFilm} className="me-2" />
                            {mvMode ? "Stop Music Videos" : "Play Music Video"}
                        </button>
                        <button
                            type="button"
                            className="btn btn-outline-light btn-sm"
                            onClick={toggleLike}
                            aria-label={liked ? "Unlike" : "Like"}
                        >
                            <FontAwesomeIcon
                                icon={liked ? faHeart : faHeartOutline}
                            />
                        </button>
                    </div>
                    <div className="current-track-header flex-row align-items-center justify-content-start w-100">
                        <div className="ml-2 flex-shrink-5 h-100 d-flex flex-column align-items-center justify-content-end text-center fade-right w-100">
                            <span
                                className="text-white no-wrap w-100"
                                style={{
                                    overflow: "hidden",
                                    whiteSpace: "nowrap",
                                    fontWeight: 800,
                                }}
                            >
                                {currentTrack.title}
                            </span>
                            <span
                                className="text-white no-wrap mb-0 w-100"
                                style={{
                                    overflow: "hidden",
                                    whiteSpace: "nowrap",
                                    fontSize: "0.9rem",
                                    opacity: 0.85,
                                }}
                            >
                                {currentTrack.album} by {currentTrack.artist}
                            </span>
                        </div>
                    </div>
                </div>
                <div className="list-group now-playing-queue scrollable">
                    {playlist &&
                        playlist.entry.length > 0 &&
                        playlist.entry.map((s) => (
                            <PlaylistEntry
                                key={s.id}
                                state={{ id: "current" }}
                                item={s}
                                playlist={playlist}
                                currentTrack={currentTrack}
                                refreshPlaylist={() => {}}
                                actionable={false}
                                style={undefined}
                            />
                        ))}
                </div>
            </div>

            <FocusContext.Provider value={focusKey}>
                <div
                    className="now-playing-controls d-flex flex-row align-items-center justify-content-center"
                    ref={ref}
                >
                    <TVActionButton
                        func={seekBackward}
                        content={
                            <>
                                <FontAwesomeIcon icon={faBackward} /> 10s
                            </>
                        }
                    />
                    <TVActionButton
                        func={playPrev}
                        content={<FontAwesomeIcon icon={faBackwardStep} />}
                    />
                    <TVActionButton
                        func={togglePlaying}
                        content={
                            <FontAwesomeIcon icon={playing ? faPause : faPlay} />
                        }
                        preferred={true}
                    />
                    <TVActionButton
                        func={playNext}
                        content={<FontAwesomeIcon icon={faForwardStep} />}
                    />
                    <TVActionButton
                        func={seekForward}
                        content={
                            <>
                                <FontAwesomeIcon icon={faForward} /> 10s
                            </>
                        }
                    />
                </div>
            </FocusContext.Provider>
            <div className="now-playing-scrubber d-flex flex-column">
                <div className="d-flex flex-row justify-content-between text-white small">
                    <span>{SecondsToHHSS(positionSec)}</span>
                    <span>{SecondsToHHSS(currentTrack.duration)}</span>
                </div>
                <input
                    type="range"
                    className="w-100"
                    min={0}
                    max={1}
                    step={0.01}
                    value={playtime}
                    onChange={(e) => changePlayTime(e)}
                />
            </div>
        </div>
    );
}
