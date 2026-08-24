import { PluginListenerHandle } from "@capacitor/core";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import VLC from "../Plugins/VLC";

export function TVTopBar() {
    const [websocketConnected, setWebsocketConnected] =
        useState<boolean>(false);
    const listener = useRef<PluginListenerHandle>();
    const navigate = useNavigate();
    useEffect(() => {
        const fetch = async () => {
            if (listener.current) {
                listener.current.remove();
            }
            listener.current = await VLC.addListener(
                "webSocketConnection",
                (info: any) => {
                    setWebsocketConnected(info.connected);
                    if (info.connected) {
                        navigate("/playing");
                    }
                }
            );
        };
        fetch();
    }, [navigate]);

    return (
        <div className="d-flex w-100 justify-content-between align-items-center my-2">
            <div style={{ margin: "auto" }}></div>
            <img
                alt="SonicSound"
                src="/logo192.png"
                style={{
                    height: "4vh",
                    filter: "invert(1)",
                }}
            />
            <span className="section-header px-3 text-white">SonicSound</span>
            <div style={{ margin: "auto" }}></div>
            {websocketConnected && (
                <i
                    className="ri-smartphone-line text-white me-2"
                    style={{ fontSize: "2rem" }}
                ></i>
            )}
        </div>
    );
}
