import "./AlbumCard.scss";
import "../Styles/colors.scss";
import { useCallback, useEffect, useState } from "react";
import VLC from "../Plugins/VLC";
import Loading from "./Loading";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";
import { useNavigate } from "react-router-dom";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";


export default function PlaylistItemCard({ item, parentRef }: { item: IPlaylist, parentRef?: React.RefObject<any> }) {
    const [coverArt, setCoverArt] = useState<string>("");
    const navigate = useNavigate();
    const openDetail = useCallback(() => {
        navigate("/playlist", { state: { id: item.id } });
    }, [item.id, navigate]);
    const { focused, ref } = useFocusable({ onEnterPress: openDetail });

    useEffect(() => {
        if (focused) {
            parentRef?.current.scrollIntoView({ behavior: "smooth", block: "end", inline: "center" });
            ref.current.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" });
        }
    }, [focused, parentRef, ref]);
    useEffect(() => {
        const func = async () => {
            const s = await VLC.getAlbumArt({ id: item.coverArt });
            if (s.status === "ok") {
                setCoverArt(s.value!);
            }
        }
        func();
    }, [item]);



    return (
        <div ref={ref} className={classNames("d-flex", "flex-column", "align-items-center", "justify-content-between", "not-selectable" ,focused ? "album-item-focused" : "", "album-item")}
            onClick={() => openDetail()}>
            <div className="d-flex align-items-center justify-content-center album-image-container">
                {coverArt === "" ? <Loading></Loading> : <img alt="" src={coverArt} className="album-image"></img>}
            </div>
            <div className=" d-flex flex-column align-items-start justify-content-end text-white no-overflow">
                <span>
                    {item.name}
                </span>
                <span>
                    {item.comment && item.comment !== "" ? item.comment : `${item.songCount} songs`}
                </span>
            </div>

        </div>
    )
}