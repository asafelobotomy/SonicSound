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
        <div className="d-flex w-100 justify-content-end align-items-center my-2 px-2">
            {websocketConnected && (
                <i
                    className="ri-smartphone-line text-white me-2"
                    style={{ fontSize: "2rem" }}
                ></i>
            )}
        </div>
    );
}
