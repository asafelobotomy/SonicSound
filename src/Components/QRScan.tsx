import { PluginListenerHandle } from "@capacitor/core";
import { Toast } from "@capacitor/toast";
import { faTv } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import VLC from "../Plugins/VLC";

/**
 * Pair a phone with an Android TV instance on the LAN.
 * Camera QR scanning was removed with Cap 6 (legacy scanner peer conflict);
 * UDP discovery + manual IP entry cover the same flow.
 */
export default function QRScan() {
    const [nearTvs, setNearTvs] = useState<string[]>([]);
    const [manualIp, setManualIp] = useState("");
    const vlcHandler = useRef<PluginListenerHandle>();
    const registered = useRef(false);
    const navigate = useNavigate();

    useEffect(() => {
        const f = async () => {
            if (vlcHandler.current !== undefined) {
                await vlcHandler.current.remove();
            }
            vlcHandler.current = await VLC.addListener(
                "tvPacket",
                (info: { ip: string }) => {
                    setNearTvs((prev) =>
                        prev.includes(info.ip) ? prev : [...prev, info.ip]
                    );
                }
            );
            if (!registered.current) {
                setTimeout(async () => {
                    try {
                        await VLC.sendUdpBroadcast();
                    } catch {
                        /* PWA stub */
                    }
                    registered.current = true;
                }, 500);
            }
        };
        f();
        return () => {
            vlcHandler.current?.remove();
        };
    }, []);

    const connect = useCallback(
        async (ip: string) => {
            const ret = await VLC.qrLogin({ ip, mode: "remote" });
            if (ret.status === "error") {
                Toast.show({ text: ret.error });
            } else {
                navigate("/home");
            }
        },
        [navigate]
    );

    const onManualSubmit = (e: FormEvent) => {
        e.preventDefault();
        if (manualIp.trim()) connect(manualIp.trim());
    };

    return (
        <div className="d-flex flex-column h-100 w-100 align-items-center justify-content-center p-3">
            <form
                className="d-flex flex-row w-100 mb-3"
                style={{ maxWidth: 420 }}
                onSubmit={onManualSubmit}
            >
                <input
                    className="form-control me-2"
                    placeholder="TV IP address"
                    value={manualIp}
                    onChange={(e) => setManualIp(e.target.value)}
                />
                <button className="btn btn-primary" type="submit">
                    Connect
                </button>
            </form>
            {nearTvs.length > 0 && (
                <>
                    <span className="subtitle text-white m-3">
                        Tap on any item to connect without typing an IP
                    </span>
                    {nearTvs.map((s) => (
                        <div
                            key={s}
                            className="list-group-item text-center text-white"
                            onClick={() => connect(s)}
                        >
                            <FontAwesomeIcon icon={faTv} /> {s}
                        </div>
                    ))}
                </>
            )}
        </div>
    );
}
