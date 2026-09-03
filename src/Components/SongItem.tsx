import "./AlbumCard.scss";
import "../Styles/colors.scss";
import { IAlbumSongResponse } from "../Models/API/Responses/IArtistResponse";
import { SecondsToHHSS } from "../Helpers";
import { useCallback, useContext, useEffect, useRef, useState } from "react";
import classNames from "classnames";
import "./SongItem.scss";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faArrowDown, faVolumeHigh } from "@fortawesome/free-solid-svg-icons";
import { MenuContext } from "../AppContext";
import VLC from "../Plugins/VLC";
import { Toast } from "@capacitor/toast";
import { PluginListenerHandle } from "@capacitor/core";
import { useAddToPlaylist } from "../Hooks/useAddToPlaylist";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";

export default function SongItem({
    item,
    currentTrack,
}: {
    item: IAlbumSongResponse;
    currentTrack: IAlbumSongResponse;
}) {
    const { setMenuContext } = useContext(MenuContext);
    const addToPlaylist = useAddToPlaylist();
    const vlcListener = useRef<PluginListenerHandle>();
    const [downloadProgress, setDownloadProgress] = useState<number>(0);
    const [cached, setCached] = useState<boolean>(false);

    const playRadio = useCallback(async () => {
        const s = await VLC.playRadio({ song: item.id });
        if (s.status === "error") {
            await Toast.show({ text: s.error });
        }
    }, [item]);

    const play = useCallback(async () => {
        const s = await VLC.playAlbum({
            album: item.albumId,
            track: item.track - 1,
        });
        if (s.status === "error") {
            await Toast.show({ text: s.error });
        }
    }, [item]);

    const openMenu = useCallback(
        (x: number, y: number) => {
            setMenuContext({
                x: `${x}px`,
                y: `${y}px`,
                show: true,
                body: (
                    <div className="d-flex flex-column">
                        <button
                            className="btn btn-primary"
                            onClick={() => {
                                playRadio();
                            }}
                        >
                            Start radio
                        </button>
                        <button
                            className="btn btn-primary"
                            onClick={() => {
                                addToPlaylist(item.id);
                            }}
                        >
                            Add to Playlist
                        </button>
                    </div>
                ),
            });
        },
        [addToPlaylist, item.id, playRadio, setMenuContext]
    );

    const { ref, focused: _focused } = useFocusable({
        onEnterPress: play,
    });

    useEffect(() => {
        const f = async () => {
            if (vlcListener.current) {
                await vlcListener.current.remove();
            }
            vlcListener.current = await VLC.addListener(
                `progress${item.id}`,
                (info: { progress: number }) => {
                    setDownloadProgress(info.progress);
                    if (info.progress >= 99) {
                        setCached(true);
                    }
                }
            );
            const status = await VLC.getSongStatus({ id: item.id });
            if (status.status === "ok") {
                setCached(status.value!!);
            }
        };
        f();
    }, [item]);

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const func = (ev: Event) => {
            const mouse = ev as MouseEvent;
            openMenu(mouse.pageX, mouse.pageY);
        };
        el.addEventListener("contextmenu", func);
        return () => {
            el.removeEventListener("contextmenu", func);
        };
    }, [openMenu, ref]);

    return (
        <div
            ref={ref}
            className={classNames(
                "list-group-item",
                currentTrack.id === item.id && "highlight",
                "not-selectable"
            )}
            onClick={() => play()}
        >
            <div className="row align-items-center">
                <div className="col-auto">{item.track}</div>
                <div className="col">
                    {currentTrack.id === item.id && (
                        <FontAwesomeIcon icon={faVolumeHigh} />
                    )}{" "}
                    {item.title}
                </div>
                <div className="col-auto">{SecondsToHHSS(item.duration)}</div>
                {!cached && downloadProgress > 0 && (
                    <div className="col-auto">
                        <FontAwesomeIcon icon={faArrowDown} /> {downloadProgress}
                        %
                    </div>
                )}
            </div>
        </div>
    );
}
