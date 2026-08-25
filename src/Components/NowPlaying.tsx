import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import { CurrentTrackContextDefValue } from "../AudioContext";
import { SecondsToHHSS } from "../Helpers";
import "./NowPlaying.scss";
import VLC from "../Plugins/VLC";
import {
    FocusContext,
    useFocusable,
} from "@noriginmedia/norigin-spatial-navigation";
import classnames from "classnames";
import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { PluginListenerHandle } from "@capacitor/core";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import { PlaylistEntry } from "./PlaylistEntry";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
    faBackward,
    faBackwardStep,
    faFilm,
    faForward,
    faForwardStep,
    faPause,
    faPlay,
} from "@fortawesome/free-solid-svg-icons";
import { Toast } from "@capacitor/toast";
import YoutubeSyncedEmbed from "./YoutubeSyncedEmbed";
import { searchMusicVideo } from "../youtube/match";

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
            const key = settings.value?.youtubeApiKey ?? "";
            const enabled = settings.value?.youtubeVideosEnabled ?? false;
            const allowAny = settings.value?.youtubeAllowAnyChannel ?? false;
            if (!enabled || !key) {
                Toast.show({
                    text: "Enable Videos and set a YouTube API key in Settings.",
                });
                setMvMode(false);
                setVideoActive(false);
                setVideoId(null);
                await applyServerAudio(true);
                return;
            }
            const match = await searchMusicVideo(
                key,
                track.artist,
                track.title,
                allowAny
            );
            if (!mvModeRef.current) return;
            if (!match) {
                // Stay in MV mode; play this queue item from the server.
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
            if (mvModeRef.current && currentTrack.id) {
                await applyServerAudio(false);
                await VLC.pause();
                await loadMusicVideoForTrack(currentTrack);
            }
        };
        fetch();
    }, [currentTrack, loadMusicVideoForTrack, applyServerAudio]);

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
        <div className="d-flex flex-column align-items-center justify-content-between h-100 w-100">
            <div className="m-auto"></div>
            <div
                className="d-flex flex-row justify-content-around align-items-center w-100"
                style={{ height: "auto" }}
            >
                <div className="d-flex flex-column align-items-center justify-content-center w-50">
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
                    <button
                        type="button"
                        className="btn btn-outline-light btn-sm mt-2"
                        onClick={toggleMusicVideo}
                    >
                        <FontAwesomeIcon icon={faFilm} className="me-2" />
                        {mvMode ? "Stop Music Videos" : "Play Music Video"}
                    </button>
                    <div className="current-track-header flex-row align-items-center justify-content-start">
                        <div className="ml-2 flex-shrink-5 h-100 d-flex flex-column align-items-start justify-content-end text-center fade-right">
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
                                }}
                            >
                                {currentTrack.album} by {currentTrack.artist}
                            </span>
                        </div>
                    </div>
                </div>
                <div className="list-group playlist no-scrollable w-50">
                    {playlist &&
                        playlist.entry.length > 0 &&
                        playlist.entry.map((s) => (
                            <PlaylistEntry
                                state={undefined}
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

            <div className="m-auto"></div>
            <FocusContext.Provider value={focusKey}>
                <div
                    className="d-flex flex-row align-items-start justify-content-center p-0"
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
            <div className="w-50 d-flex flex-row justify-content-between text-white">
                <span>{SecondsToHHSS(positionSec)}</span>
                <span>{SecondsToHHSS(currentTrack.duration)}</span>
            </div>
            <div className="w-50" style={{ marginBottom: "30px" }}>
                <input
                    disabled
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

interface TVActionButtonProps {
    func: () => void;
    content: any;
    preferred?: boolean;
}

function TVActionButton({ func, content, preferred }: TVActionButtonProps) {
    const { ref, focused, focusSelf } = useFocusable({ onEnterPress: func });
    useEffect(() => {
        if (preferred) {
            focusSelf();
        }
    }, [preferred, focusSelf]);
    return (
        <div
            ref={ref}
            className={classnames(
                "m-2",
                "p-2",
                "text-white",
                "tv-button",
                focused ? "btn-tv-selected" : ""
            )}
            onClick={func}
        >
            <div className="d-flex flex-column align-items-center ">{content}</div>
        </div>
    );
}
