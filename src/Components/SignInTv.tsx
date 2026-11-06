import { PluginListenerHandle } from "@capacitor/core";
import { faTv } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { FormEvent, useEffect, useRef, useState } from "react";

import VLC from "../Plugins/VLC";

interface IQuickConnectTv {
    ip: string;
    deviceName: string;
    wsPort?: number;
}

/**
 * Quick Connect: a TV on its login screen shows a 6-digit code; entering it here signs
 * the TV in with this phone's account (encrypted on the wire, no typing on the TV).
 */
export default function SignInTv() {
    const [tvs, setTvs] = useState<IQuickConnectTv[]>([]);
    const [ip, setIp] = useState("");
    const [code, setCode] = useState("");
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
    const listener = useRef<PluginListenerHandle>();

    useEffect(() => {
        const setup = async () => {
            const list = await VLC.getQuickConnectTvs();
            if (list.status === "ok" && list.value) setTvs(list.value);
            listener.current = await VLC.addListener(
                "quickConnectTvsUpdated",
                (info: { devices: IQuickConnectTv[] }) => setTvs(info.devices ?? [])
            );
        };
        setup();
        return () => {
            listener.current?.remove();
        };
    }, []);

    // Preselect the only TV waiting for a code.
    useEffect(() => {
        if (!ip && tvs.length === 1) setIp(tvs[0].ip);
    }, [tvs, ip]);

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault();
        const digits = code.replace(/\D/g, "");
        if (!ip.trim() || digits.length !== 6) {
            setResult({ ok: false, text: "Pick a TV and enter the 6-digit code it shows." });
            return;
        }
        setBusy(true);
        setResult({ ok: true, text: "Sending to the TV…" });
        const tv = tvs.find((t) => t.ip === ip.trim());
        const ret = await VLC.quickConnectTv({ ip: ip.trim(), code: digits, wsPort: tv?.wsPort });
        setBusy(false);
        if (ret.status === "ok") {
            setResult({ ok: true, text: ret.value || "Signed in on the TV." });
            setCode("");
        } else {
            setResult({ ok: false, text: ret.error || "Couldn't sign the TV in." });
        }
    };

    return (
        <form className="w-100 mt-4" style={{ maxWidth: 420 }} onSubmit={onSubmit}>
            <h5 className="mb-2">Sign in a TV</h5>
            <p className="text-secondary small mb-2">
                On the TV, open Quick Connect on the login screen, then enter its code here.
            </p>
            {tvs.map((tv) => (
                <button
                    key={tv.ip}
                    type="button"
                    className={`btn w-100 mb-2 ${ip === tv.ip ? "btn-primary" : "btn-outline-light"}`}
                    onClick={() => setIp(tv.ip)}
                >
                    <FontAwesomeIcon icon={faTv} /> {tv.deviceName || tv.ip}
                </button>
            ))}
            {tvs.length === 0 && (
                <input
                    className="form-control mb-2"
                    placeholder="TV IP (shown under the QR code)"
                    inputMode="decimal"
                    value={ip}
                    onChange={(e) => setIp(e.target.value)}
                />
            )}
            <div className="d-flex flex-row">
                <input
                    className="form-control me-2"
                    placeholder="6-digit code"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={7}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                />
                <button className="btn btn-primary" type="submit" disabled={busy}>
                    Sign in
                </button>
            </div>
            {result && (
                <p className={`small mt-2 ${result.ok ? "text-success" : "text-danger"}`}>
                    {result.text}
                </p>
            )}
        </form>
    );
}
