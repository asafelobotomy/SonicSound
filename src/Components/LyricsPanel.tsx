import { useCallback, useEffect, useState } from "react";
import { Toast } from "@capacitor/toast";
import VLC from "../Plugins/VLC";

interface LyricsPanelProps {
    artist: string;
    title: string;
}

export default function LyricsPanel({ artist, title }: LyricsPanelProps) {
    const [open, setOpen] = useState(false);
    const [lyrics, setLyrics] = useState("");
    const [loading, setLoading] = useState(false);

    const load = useCallback(async () => {
        if (!artist || !title || !VLC.getLyrics) {
            Toast.show({ text: "Lyrics not available" });
            return;
        }
        setLoading(true);
        try {
            const ret = await VLC.getLyrics({ artist, title });
            if (ret.status === "ok") {
                setLyrics(ret.value || "No lyrics found.");
            } else {
                setLyrics(ret.error || "No lyrics found.");
            }
        } catch {
            setLyrics("Lyrics unavailable on this client.");
        } finally {
            setLoading(false);
        }
    }, [artist, title]);

    useEffect(() => {
        if (open) load();
    }, [open, load]);

    return (
        <div className="w-100 text-start">
            <button
                type="button"
                className="btn btn-link text-white p-0"
                onClick={() => setOpen((v) => !v)}
            >
                {open ? "Hide lyrics" : "Show lyrics"}
            </button>
            {open && (
                <pre
                    className="text-white mt-2 p-2"
                    style={{
                        whiteSpace: "pre-wrap",
                        maxHeight: 200,
                        overflow: "auto",
                        fontSize: 12,
                        background: "rgba(0,0,0,0.35)",
                    }}
                >
                    {loading ? "Loading…" : lyrics}
                </pre>
            )}
        </div>
    );
}
