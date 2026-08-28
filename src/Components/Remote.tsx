import { Capacitor, PluginListenerHandle } from "@capacitor/core";
import { Preferences } from "@capacitor/preferences";
import { Toast } from "@capacitor/toast";
import { faTv } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import VLC from "../Plugins/VLC";

export interface IRemoteDevice {
    ip: string;
    deviceName: string;
    serverUrl?: string;
    wsPort?: number;
}

const LAST_REMOTE_KEY = "lastRemoteDevice";

/**
 * Remote: discover TVs on LAN (same Navidrome account) and connect as controller.
 */
export default function Remote() {
    const [devices, setDevices] = useState<IRemoteDevice[]>([]);
    const [manualIp, setManualIp] = useState("");
    const [connected, setConnected] = useState(false);
    const listener = useRef<PluginListenerHandle>();
    const navigate = useNavigate();

    useEffect(() => {
        const setup = async () => {
            const status = await VLC.getWebsocketStatus();
            setConnected(status.status === "ok" && !!status.value);
            await VLC.startRemoteDiscovery();
            const list = await VLC.getDiscoveredRemotes();
            if (list.status === "ok" && list.value) {
                setDevices(list.value as IRemoteDevice[]);
            }
            listener.current = await VLC.addListener(
                "remoteDevicesUpdated",
                (info: { devices: IRemoteDevice[] }) => {
                    setDevices(info.devices ?? []);
                }
            );
            const last = await Preferences.get({ key: LAST_REMOTE_KEY });
            if (last.value && !status.value) {
                try {
                    const parsed = JSON.parse(last.value) as IRemoteDevice;
                    await connect(parsed.ip, parsed.deviceName, false);
                } catch {
                    /* ignore */
                }
            }
        };
        setup();
        return () => {
            listener.current?.remove();
            VLC.stopRemoteDiscovery();
        };
    }, []);

    const connect = useCallback(
        async (ip: string, deviceName?: string, navigateHome = true) => {
            const ret = await VLC.connectRemote({ ip, deviceName });
            if (ret.status === "error") {
                Toast.show({ text: ret.error });
                return;
            }
            setConnected(true);
            await Preferences.set({
                key: LAST_REMOTE_KEY,
                value: JSON.stringify({ ip, deviceName: deviceName ?? ip }),
            });
            if (navigateHome) navigate("/home");
        },
        [navigate]
    );

    const onManualSubmit = (e: FormEvent) => {
        e.preventDefault();
        if (manualIp.trim()) connect(manualIp.trim());
    };

    const disconnect = async () => {
        await VLC.disconnectWebsocket();
        setConnected(false);
        Toast.show({ text: "TV disconnected" });
    };

    return (
        <div className="d-flex flex-column h-100 w-100 align-items-center p-3 text-white">
            <h2 className="mb-3">Remote</h2>
            <p className="text-center text-secondary mb-4">
                Phones on the same Navidrome account auto-discover your TV.
                Pick an output below.
            </p>
            {connected && (
                <button className="btn btn-outline-light mb-3" onClick={disconnect}>
                    Disconnect from TV
                </button>
            )}
            {devices.length > 0 ? (
                devices.map((d) => (
                    <button
                        key={d.ip}
                        className="btn btn-primary w-100 mb-2"
                        style={{ maxWidth: 420 }}
                        onClick={() => connect(d.ip, d.deviceName)}
                    >
                        <FontAwesomeIcon icon={faTv} /> {d.deviceName || d.ip}
                    </button>
                ))
            ) : (
                <p className="text-secondary">Searching for TVs on your network…</p>
            )}
            <form
                className="d-flex flex-row w-100 mt-4"
                style={{ maxWidth: 420 }}
                onSubmit={onManualSubmit}
            >
                <input
                    className="form-control me-2"
                    placeholder="TV IP (manual)"
                    value={manualIp}
                    onChange={(e) => setManualIp(e.target.value)}
                />
                <button className="btn btn-secondary" type="submit">
                    Connect
                </button>
            </form>
        </div>
    );
}
