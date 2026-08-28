import {
    faBroadcastTower,
    faCompactDisc,
    faFilm,
    faGear,
    faHouse,
    faListUl,
    faMagnifyingGlass,
    faQrcode,
    faUser,
    faUsers,
} from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import classnames from "classnames";
import { Dispatch, SetStateAction } from "react";
import "./Sidebar.scss";
import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import { Capacitor } from "@capacitor/core";
import logo from "../assets/logo.png";
import { Features } from "../features";

export default function Sidebar({
    setNavbarCollapsed,
    navbarCollapsed,
}: {
    navbarCollapsed: boolean;
    setNavbarCollapsed: Dispatch<SetStateAction<boolean>>;
}) {
    const navigate = useNavigate();
    const variants = {
        visible: { opacity: 1, scale: 1 },
        hidden: { opacity: 0, scale: 0 },
    };

    const nav = (path: string) => {
        setNavbarCollapsed(true);
        // Replace so sidebar switches don't stack unrelated pages on Back.
        navigate(path, { replace: true });
    };

    const item = (path: string, icon: any, last = false) => (
        <div
            onClick={() => nav(path)}
            className={classnames(
                "sidebar-item",
                last && "last-item",
                "d-flex",
                "align-items-center",
                "justify-content-center",
                "text-white"
            )}
        >
            <FontAwesomeIcon icon={icon} />
        </div>
    );

    return (
        <>
            <motion.div
                initial="hidden"
                animate={navbarCollapsed ? "hidden" : "visible"}
                variants={variants}
                className={classnames(
                    "d-flex",
                    "flex-column",
                    "sidebar",
                    "align-items-center",
                    "justify-content-start"
                )}
                transition={{ duration: 0.05 }}
            >
                <div
                    className="sidebar-item-borderless d-flex align-items-center justify-content-center"
                    aria-hidden
                >
                    <img src={logo} alt="" className="sidebar-logo" />
                </div>
                {item("/home", faHouse)}
                {item("/artists", faUsers)}
                {item("/albums", faCompactDisc)}
                {item("/search", faMagnifyingGlass)}
                {item("/playlists", faListUl)}
        {item("/radio", faBroadcastTower)}
        {item("/jukebox", faListUl)}
        {Features.youtubeMusicVideos && item("/videos", faFilm)}
        {Capacitor.getPlatform() === "android" && item("/remote", faQrcode)}
                {item("/settings", faGear)}
                {item("/account", faUser, true)}
            </motion.div>
            <div
                onClick={() => setNavbarCollapsed(true)}
                className={`${navbarCollapsed ? "d-none" : "modal-cover"}`}
            ></div>
        </>
    );
}
