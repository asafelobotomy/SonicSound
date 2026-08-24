import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { AppContext } from "../AppContext";
import { SecondsToHHSS } from "../Helpers";
import {
    IAlbumSongResponse,
    IInnerAlbumResponse,
} from "../Models/API/Responses/IArtistResponse";
import "./Album.scss";
import SongItem from "./SongItem";
import Loading from "./Loading";
import { Helmet } from "react-helmet";
import VLC from "../Plugins/VLC";
import { Toast } from "@capacitor/toast";
import { CurrentTrackContextDefValue } from "../AudioContext";
import { PluginListenerHandle } from "@capacitor/core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
    faCloudArrowDown,
    faPlay,
    faShuffle,
} from "@fortawesome/free-solid-svg-icons";

export default function Album() {
    const [album, setAlbum] = useState<IInnerAlbumResponse>();
    const [albumFetched, setAlbumFetched] = useState<boolean>();
    const { context } = useContext(AppContext);
    const { state }: any = useLocation();
    const [imgDimentions, setImgDimentions] = useState<any>();
    const [coverArt, setCoverArt] = useState<string>("");
    const [currentTrack, setCurrentTrack] = useState<IAlbumSongResponse>(
        CurrentTrackContextDefValue
    );
    const listener = useRef<PluginListenerHandle>();
    useEffect(() => {
        const fetch = async () => {
            if (state.id === 0 || !state.id) {
                setAlbumFetched(true);
                return;
            }
            const ret = await VLC.getAlbum({ id: state.id });
            if (ret.status === "ok") {
                setAlbum(ret.value!);
                const coverId = ret.value!.coverArt || state.id;
                const art = await VLC.getAlbumArt({ id: coverId });
                if (art.status === "ok") {
                    setCoverArt(art.value!);
                } else {
                    Toast.show({ text: art.error });
                }
            } else {
                Toast.show({ text: ret.error });
            }
            setAlbumFetched(true);
            if (listener.current) {
                await listener.current.remove();
            }
            listener.current = await VLC.addListener(
                "currentTrack",
                (info: any) => {
                    setCurrentTrack(info.currentTrack);
                }
            );
        };
        fetch();
    }, [albumFetched, context, state.id]);

    const onLoadImage = useCallback((ev: any) => {
        if (ev.target.height >= ev.target.width) {
            setImgDimentions({
                height: "20vh",
                width: "auto",
            });
        } else {
            setImgDimentions({
                height: "auto",
                maxWidth: "20vh",
            });
        }
    }, []);

    const downloadAlbum = useCallback(async () => {
        Toast.show({ text: "Downloading album" });
        const ret = await VLC.downloadAlbum({ id: state.id });
        if (ret.status === "error") {
            Toast.show({ text: ret.error });
        }
    }, [state]);

    const playAll = useCallback(async () => {
        const ret = await VLC.playAlbum({ album: state.id, track: 0 });
        if (ret.status === "error") Toast.show({ text: ret.error });
    }, [state.id]);

    const shuffleAll = useCallback(async () => {
        const ret = await VLC.playAlbum({ album: state.id, track: 0 });
        if (ret.status === "error") {
            Toast.show({ text: ret.error });
            return;
        }
        await VLC.shufflePlaylist();
    }, [state.id]);

    if (!albumFetched) {
        return (
            <div className="row">
                <div
                    className="col-12 d-flex align-items-center justify-content-center"
                    style={{ height: "100%" }}
                >
                    <Loading />
                </div>
            </div>
        );
    }

    return (
        <>
            <Helmet>
                <title>{album?.name} - SonicSound</title>
            </Helmet>
            <div className="album-header d-flex flex-row align-items-center justify-content-start">
                <img
                    alt=""
                    className="album-img"
                    src={coverArt}
                    style={{ ...imgDimentions }}
                    onLoad={onLoadImage}
                ></img>
                <div className="ml-2 mb-2 h-100 flex-column align-items-start justify-content-between hide-desktop-flex">
                    <div className="d-flex flex-row gap-2">
                        <button
                            className="btn btn-primary text-white"
                            onClick={playAll}
                            title="Play all"
                        >
                            <FontAwesomeIcon icon={faPlay} />
                        </button>
                        <button
                            className="btn btn-primary text-white"
                            onClick={shuffleAll}
                            title="Shuffle"
                        >
                            <FontAwesomeIcon icon={faShuffle} />
                        </button>
                        <button
                            className="btn btn-primary text-white"
                            onClick={downloadAlbum}
                            title="Download"
                        >
                            <FontAwesomeIcon icon={faCloudArrowDown} />
                        </button>
                    </div>
                    <div className="d-flex flex-column align-items-start justify-content-end">
                        <span className="text-white text-start text-header-mobile">
                            {album?.name}
                        </span>
                        <span className="text-white text-start">
                            by {album?.artist}
                        </span>
                    </div>
                </div>
                <div className="ml-2 h-100 flex-column align-items-start justify-content-between hide-mobile-flex">
                    <span className="text-white text-start text-header">
                        {album?.name}
                    </span>
                    <div className="d-flex flex-column align-items-start justify-content-end">
                        <span className="text-white text-start">
                            by {album?.artist}
                        </span>
                        <span className="text-white text-start">
                            {SecondsToHHSS(album?.duration ?? 0)}
                        </span>
                        <span className="text-white text-start">
                            {album?.songCount} songs
                        </span>
                        <span className="text-white text-start">
                            released on {album?.year}
                        </span>
                        <div className="d-flex flex-row gap-2 mt-2">
                            <button
                                className="btn btn-primary btn-sm"
                                onClick={playAll}
                            >
                                <FontAwesomeIcon icon={faPlay} /> Play
                            </button>
                            <button
                                className="btn btn-primary btn-sm"
                                onClick={shuffleAll}
                            >
                                <FontAwesomeIcon icon={faShuffle} /> Shuffle
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            <div
                className="scrollable"
                style={{ height: "100%", overflow: "auto" }}
            >
                <div className="list-group">
                    {album &&
                        album?.song.map((s) => (
                            <SongItem
                                item={s}
                                key={s.id}
                                currentTrack={currentTrack}
                            />
                        ))}
                </div>
            </div>
        </>
    );
}
