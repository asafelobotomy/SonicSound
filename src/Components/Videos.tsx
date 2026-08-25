import { useCallback, useEffect, useState } from "react";
import VLC from "../Plugins/VLC";
import { Toast } from "@capacitor/toast";

interface YtItem {
    id: string;
    title: string;
    channel: string;
    thumb: string;
}

const YT_PREMIUM = "https://www.youtube.com/premium";

export default function Videos() {
    const [query, setQuery] = useState("");
    const [items, setItems] = useState<YtItem[]>([]);
    const [hint, setHint] = useState(
        "Enable Videos and set a YouTube Data API key in Settings."
    );

    const search = useCallback(async () => {
        const settings = await VLC.getSettings();
        const key = settings.value?.youtubeApiKey ?? "";
        const enabled = settings.value?.youtubeVideosEnabled ?? false;
        if (!enabled || !key) {
            setHint(
                "Enable Videos and set a YouTube Data API key in Settings."
            );
            setItems([]);
            return;
        }
        if (!query.trim()) return;
        try {
            const url =
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20" +
                `&q=${encodeURIComponent(query)}&key=${encodeURIComponent(key)}`;
            const res = await fetch(url);
            if (!res.ok) {
                Toast.show({ text: "YouTube API request failed" });
                return;
            }
            const data = await res.json();
            const mapped: YtItem[] = (data.items ?? [])
                .map((it: any) => ({
                    id: it?.id?.videoId as string,
                    title: it?.snippet?.title as string,
                    channel: it?.snippet?.channelTitle as string,
                    thumb:
                        it?.snippet?.thumbnails?.medium?.url ??
                        it?.snippet?.thumbnails?.default?.url ??
                        "",
                }))
                .filter((it: YtItem) => !!it.id);
            setItems(mapped);
            setHint(
                mapped.length
                    ? "Opens in YouTube — Premium accounts play without ads."
                    : "No results."
            );
        } catch {
            Toast.show({ text: "YouTube search failed" });
        }
    }, [query]);

    useEffect(() => {
        /* load hint from settings once */
        VLC.getSettings().then((s) => {
            if (s.value?.youtubeVideosEnabled && s.value?.youtubeApiKey) {
                setHint("Search official YouTube results.");
            }
        });
    }, []);

    return (
        <div className="d-flex flex-column p-3 overflow-scroll scrollable">
            <div className="section-header text-white">Music videos</div>
            <div className="d-flex flex-row gap-2 mb-2">
                <input
                    className="form-control"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") search();
                    }}
                    placeholder="Search YouTube…"
                />
                <button type="button" className="btn btn-primary" onClick={search}>
                    Search
                </button>
            </div>
            <div className="subtitle text-white mb-2">{hint}</div>
            <a
                className="btn btn-outline-light btn-sm align-self-start mb-3"
                href={YT_PREMIUM}
                target="_blank"
                rel="noreferrer"
            >
                YouTube Premium (ad-free)
            </a>
            <div className="d-flex flex-column gap-2">
                {items.map((it) => (
                    <a
                        key={it.id}
                        className="d-flex flex-row align-items-center text-decoration-none text-white"
                        href={`https://www.youtube.com/watch?v=${it.id}`}
                        target="_blank"
                        rel="noreferrer"
                    >
                        {it.thumb ? (
                            <img
                                src={it.thumb}
                                alt=""
                                style={{ width: 120, height: 68, objectFit: "cover" }}
                                className="me-3"
                            />
                        ) : null}
                        <div>
                            <div>{it.title}</div>
                            <div className="subtitle">{it.channel}</div>
                        </div>
                    </a>
                ))}
            </div>
        </div>
    );
}
