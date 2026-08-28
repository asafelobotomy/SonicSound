import {
    faForwardStep,
    faPause,
    faPlay,
    faRepeat,
    faShuffle,
    faVolumeHigh,
    faVolumeLow,
} from "@fortawesome/free-solid-svg-icons";
import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import { CurrentTrackContextDefValue } from "../AudioContext";
import { SecondsToHHSS } from "../Helpers";
import "./AudioControl.scss";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useLocation, useNavigate } from "react-router-dom";
import classnames from "classnames";
import VLC from "../Plugins/VLC";
import { Capacitor, PluginListenerHandle } from "@capacitor/core";
import AndroidTVPlugin from "../Plugins/AndroidTV";
import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { Toast } from "@capacitor/toast";
import LyricsPanel from "./LyricsPanel";

export default function AudioControl() {
    const [currentTrack, setCurrentTrack] = useState<IAlbumSongResponse>(
        CurrentTrackContextDefValue
    );
    const [playing, setPlaying] = useState<boolean>(false);
    const [playtime, setPlaytime] = useState<number>(0);
    const [displayPlaytime, setDisplayPlaytime] = useState<number>(0);
    const [shuffle, setShuffle] = useState<boolean>(false);
    const [repeatMode, setRepeatMode] = useState<"off" | "all" | "one">("off");
    const progressAnchor = useRef({ fraction: 0, at: 0 });
    const [coverArt, setCoverArt] = useState<string>("");
    const [androidTV, setAndroidTV] = useState<boolean>(false);
    const location = useLocation();
    const navigate = useNavigate();
    const [volume, setVolume] = useState<number>(1);
    const listeners = useRef<PluginListenerHandle[]>([]);

    const changeVolume = useCallback(
        async (e: ChangeEvent<HTMLInputElement>) => {
            const vol = parseFloat(e.target.value);
            setVolume(vol);
            await VLC.setVolume({ volume: vol });
        },
        []
    );

    const changePlayTime = useCallback(
        (e: ChangeEvent<HTMLInputElement>): void => {
            const time = parseFloat(e.target.value);
            progressAnchor.current = { fraction: time, at: performance.now() };
            setDisplayPlaytime(time);
            setPlaytime(time);
            VLC.seek({ time: time });
        },
        []
    );
    useEffect(() => {
        const fetch = async () => {
            try {
                if (Capacitor.isPluginAvailable("AndroidTV")) {
                    setAndroidTV((await AndroidTVPlugin.get()).value);
                }
            } catch (e: any) {}
        };
        fetch();
    }, []);

    useEffect(() => {
        if (currentTrack.id === "") {
            return;
        }
        const fetch = async () => {
            const coverId = currentTrack.coverArt || currentTrack.albumId;
            if (!coverId) return;
            setCoverArt((await VLC.getAlbumArt({ id: coverId })).value!);
        };
        fetch();
    }, [currentTrack]);

    useEffect(() => {
        const get = async () => {
            const current = await VLC.getCurrentState();
            if (current.status === "ok") {
                setCurrentTrack(current.value?.currentTrack!);
                setPlaying(current.value?.playing!);
                setPlaytime(current.value?.playtime!);
                setDisplayPlaytime(current.value?.playtime ?? 0);
                setRepeatMode(current.value?.repeatMode ?? "off");
            }
            VLC.addListener("EX", (info) => {
                Toast.show({ text: info.error });
            });
        };
        setTimeout(() => get(), 500);
    }, []);

    const playNext = useCallback(() => {
        VLC.next();
    }, []);

    const playPrev = useCallback(() => {
        VLC.prev();
    }, []);

    const shufflePlaylist = useCallback(() => {
        VLC.shufflePlaylist();
    }, []);

    const cycleRepeat = useCallback(() => {
        VLC.cycleRepeat();
    }, []);

    const togglePlaying = () => {
        if (playing) {
            VLC.pause();
        } else {
            VLC.play();
        }
    };

    const goToAlbum = useCallback(() => {
        navigate(`/album`, { state: { id: currentTrack.parent } });
    }, [currentTrack.parent, navigate]);

    const hide = useCallback(() => {
        if (currentTrack.id === "" || location.pathname.match(/playing/)) {
            return "d-none";
        }
        return "d-flex";
    }, [currentTrack, location.pathname]);
    useEffect(() => {
        const aw = async () => {
            listeners.current.forEach(async (listener) => {
                await listener.remove();
            });
            listeners.current = [
                await VLC.addListener("play", (info: any) => {
                    setPlaying(true);
                }),
                await VLC.addListener("paused", (info: any) => {
                    setPlaying(false);
                }),
                await VLC.addListener("stopped", (info: any) => {
                    setPlaying(false);
                }),
                await VLC.addListener("currentTrack", (info: any) => {
                    setCurrentTrack(info.currentTrack);
                }),
                await VLC.addListener("progress", (info: any) => {
                    progressAnchor.current = {
                        fraction: info.time,
                        at: performance.now(),
                    };
                    setPlaytime(info.time);
                    setDisplayPlaytime(info.time);
                }),
                await VLC.addListener("playlistUpdated", async (info: any) => {
                    const state = await VLC.getCurrentState();
                    if (state.status === "ok") {
                        setShuffle(state.value!.shuffling);
                        setRepeatMode(state.value!.repeatMode ?? "off");
                    }
                }),
            ];
        };
        aw();

        return () => {
            //setCurrentTrack(CurrentTrackContextDefValue);
        };
    }, [setPlaying, setCurrentTrack, setPlaytime]);

    useEffect(() => {
        if (!playing) {
            setDisplayPlaytime(playtime);
            return;
        }
        let frame = 0;
        const tick = () => {
            const dur = currentTrack.duration || 1;
            const elapsed = (performance.now() - progressAnchor.current.at) / 1000;
            const estimated = Math.min(
                1,
                progressAnchor.current.fraction + elapsed / dur
            );
            setDisplayPlaytime(estimated);
            frame = requestAnimationFrame(tick);
        };
        frame = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(frame);
    }, [playing, playtime, currentTrack.duration]);

    return (
        <div
            className={classnames(
                "flex-column justify-content-between w-100",
                "mt-3",
                hide()
            )}
        >
            <div className="d-flex flex-row align-items-center justify-content-between w-100">
                {/* <div className="flex-shrink-1 hide-overflow" > */}
                <div
                    onClick={goToAlbum}
                    className={`current-track-header flex-row align-items-center justify-content-start ${
                        currentTrack.id === "" ? "d-none" : "d-flex"
                    }`}
                >
                    <img
                        alt=""
                        className={"current-track-img"}
                        src={coverArt}
                    ></img>
                    <div className="ml-2 flex-shrink-5  h-100 d-flex flex-column align-items-start justify-content-end text-start fade-right">
                        <span
                            className="text-white no-wrap"
                            style={{
                                overflow: "hidden",
                                whiteSpace: "nowrap",
                                fontWeight: 800,
                            }}
                        >
                            {currentTrack.title}
                        </span>
                        <span
                            className="text-white no-wrap mb-0"
                            style={{ overflow: "hidden", whiteSpace: "nowrap" }}
                        >
                            by {currentTrack.artist}
                        </span>
                    </div>
                </div>
                {/* </div> */}
                <div className="d-flex  flex-grow-1 flex-column align-items-end justify-content-end">
                    <div className="d-flex flex-row align-items-center justify-content-center p-0">
                        <button
                            type="button"
                            className={classnames(
                                "btn",
                                "btn-link",
                                "text-white",
                                shuffle ? "btn-selected" : ""
                            )}
                            onClick={shufflePlaylist}
                        >
                            <FontAwesomeIcon icon={faShuffle}></FontAwesomeIcon>
                        </button>
                        <button
                            type="button"
                            className={classnames(
                                "btn",
                                "btn-link",
                                "text-white",
                                "repeat-btn",
                                repeatMode !== "off" ? "btn-selected" : ""
                            )}
                            onClick={cycleRepeat}
                            title={
                                repeatMode === "all"
                                    ? "Repeat queue"
                                    : repeatMode === "one"
                                      ? "Repeat current track"
                                      : "Repeat off"
                            }
                        >
                            <FontAwesomeIcon icon={faRepeat} />
                            {repeatMode === "one" && (
                                <span className="repeat-one-badge">1</span>
                            )}
                        </button>
                        <button
                            type="button"
                            className="btn btn-link text-white"
                            onClick={playPrev}
                        >
                            <FontAwesomeIcon
                                flip="horizontal"
                                icon={faForwardStep}
                            ></FontAwesomeIcon>
                        </button>
                        <button
                            type="button"
                            className="btn btn-link text-white"
                            onClick={togglePlaying}
                        >
                            {playing ? (
                                <FontAwesomeIcon
                                    icon={faPause}
                                ></FontAwesomeIcon>
                            ) : (
                                <FontAwesomeIcon
                                    icon={faPlay}
                                ></FontAwesomeIcon>
                            )}
                        </button>
                        <button
                            type="button"
                            className="btn btn-link text-white"
                            onClick={playNext}
                        >
                            <FontAwesomeIcon
                                icon={faForwardStep}
                            ></FontAwesomeIcon>
                        </button>
                    </div>
                    <div
                        className={classnames(
                            "hide-mobile-flex",
                            "flex-row",
                            "align-items-center",
                            "justify-content-center",
                            androidTV ? "d-none" : ""
                        )}
                    >
                        <FontAwesomeIcon
                            icon={faVolumeLow}
                            className="text-white"
                        />
                        <input
                            type="range"
                            min={0}
                            max={1}
                            step={0.05}
                            value={volume}
                            onChange={(e) => changeVolume(e)}
                            className="mx-2"
                        ></input>
                        <FontAwesomeIcon
                            icon={faVolumeHigh}
                            className="text-white"
                        />
                    </div>
                </div>
            </div>
            <div className="w-100 d-flex flex-row justify-content-between text-white">
                <span>
                    {SecondsToHHSS(
                        displayPlaytime * (currentTrack?.duration ?? 0)
                    )}
                </span>
                <span>{SecondsToHHSS(currentTrack.duration)}</span>
            </div>
            <div className="w-100 mb-3">
                <input
                    disabled={androidTV}
                    type="range"
                    className="w-100"
                    min={0}
                    max={1}
                    step={0.001}
                    value={displayPlaytime}
                    onChange={(e) => changePlayTime(e)}
                ></input>
            </div>
            {currentTrack.id !== "" && (
                <div className="w-100 px-2 mb-2">
                    <LyricsPanel
                        artist={currentTrack.artist}
                        title={currentTrack.title}
                    />
                </div>
            )}
        </div>
    );
}
