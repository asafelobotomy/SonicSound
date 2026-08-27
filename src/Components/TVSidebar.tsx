import "./TVSidebar.scss";
import { useNavigate } from "react-router-dom";
import {
    FocusContext,
    useFocusable,
} from "@noriginmedia/norigin-spatial-navigation";
import classnames from "classnames";
import { useCallback, useEffect } from "react";
import VLC from "../Plugins/VLC";
import { Toast } from "@capacitor/toast";
import logo from "../assets/logo.png";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
    faBroadcastTower,
    faCompactDisc,
    faFilm,
    faGear,
    faHouse,
    faListUl,
    faMagnifyingGlass,
    faMusic,
    faPlayCircle,
    faUser,
    faUsers,
} from "@fortawesome/free-solid-svg-icons";
import type { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { Features } from "../features";

export default function TVSidebar() {
    const { ref, focusKey, focusSelf, hasFocusedChild } = useFocusable({
        trackChildren: true,
    });
    useEffect(() => {
        setTimeout(() => focusSelf(), 500);
    }, [focusSelf]);

    useEffect(() => {
        VLC.addListener("EX", (info) => {
            Toast.show({ text: info.error });
        });
    }, []);

    return (
        <FocusContext.Provider value={focusKey}>
            <div
                ref={ref}
                className={classnames(
                    "d-flex",
                    "flex-column",
                    hasFocusedChild ? "sidebar-tv-focused" : "sidebar-tv"
                )}
            >
                <div className="d-flex align-items-center justify-content-center p-2 mb-2">
                    <img
                        src={logo}
                        alt=""
                        style={{ height: "3rem", filter: "invert(1)" }}
                    />
                </div>
                <TVSidebarButton path="/home" icon={faHouse} text="Home" />
                <TVSidebarButton path="/artists" icon={faUsers} text="Artists" />
                <TVSidebarButton
                    path="/albums"
                    icon={faCompactDisc}
                    text="Albums"
                />
                <TVSidebarButton
                    path="/search"
                    icon={faMagnifyingGlass}
                    text="Search"
                />
                <TVSidebarButton
                    path="/tvPlaylists"
                    icon={faListUl}
                    text="Playlists"
                />
                <TVSidebarButton
                    path="/radio"
                    icon={faBroadcastTower}
                    text="Radio"
                />
                {Features.youtubeMusicVideos && (
                    <TVSidebarButton path="/videos" icon={faFilm} text="Videos" />
                )}
                <TVSidebarButton path="/account" icon={faUser} text="Account" />
                <TVSidebarButton path="/settings" icon={faGear} text="Settings" />
                <TVSidebarButton
                    path="/tvJukebox"
                    icon={faMusic}
                    text="Jukebox"
                />
                <div className="m-auto"></div>
                <TVSidebarButton
                    path="/playing"
                    icon={faPlayCircle}
                    text="Playing"
                    replace={false}
                />
            </div>
        </FocusContext.Provider>
    );
}

function TVSidebarButton({
    path,
    text,
    icon,
    replace = true,
}: {
    path: string;
    text: string;
    icon: IconDefinition;
    /** Top-level library routes replace; Now Playing pushes so Back restores. */
    replace?: boolean;
}) {
    const navigate = useNavigate();
    const nav = useCallback(() => {
        navigate(path, { replace });
    }, [path, navigate, replace]);
    const { ref, focused } = useFocusable({ onEnterPress: nav });
    return (
        <div
            ref={ref}
            onClick={nav}
            className={classnames(
                "sidebar-item",
                "d-flex",
                "align-items-center",
                "justify-content-start",
                "text-white",
                "p-3",
                "mb-3",
                focused ? "sidebar-item-focused" : ""
            )}
        >
            <FontAwesomeIcon icon={icon} className="icon-large-tv" />
            <span className="item-text">{text}</span>
        </div>
    );
}
