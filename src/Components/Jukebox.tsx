import { Toast } from "@capacitor/toast";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import VLC from "../Plugins/VLC";
import { buildCollectionJson, JukeboxTab, type ICollectionPayload } from "../models/jukeboxCollection";

export default function Jukebox() {
    const navigate = useNavigate();
    const [tab, setTab] = useState<JukeboxTab>("random");
    const [genres, setGenres] = useState<{ value: string }[]>([]);
    const [artists, setArtists] = useState<{ id: string; name: string }[]>([]);
    const [playlists, setPlaylists] = useState<{ id: string; name: string }[]>([]);
    const [remoteConnected, setRemoteConnected] = useState(false);
    const [playOnTv, setPlayOnTv] = useState(false);
    const [similarAvailable, setSimilarAvailable] = useState(true);
    const [seedTitle, setSeedTitle] = useState<string | null>(null);
    const [seedId, setSeedId] = useState<string | null>(null);

    useEffect(() => {
        const load = async () => {
            const ws = await VLC.getWebsocketStatus();
            setRemoteConnected(ws.status === "ok" && !!ws.value);
            const caps = await VLC.getServerCapabilities();
            if (caps.status === "ok" && caps.value) {
                setSimilarAvailable(!!caps.value.sonicSimilarity);
            }
            const g = await VLC.getGenres();
            if (g.status === "ok" && g.value) setGenres(g.value as { value: string }[]);
            const a = await VLC.getArtists();
            if (a.status === "ok" && a.value) {
                setArtists(
                    (a.value as { id: string; name: string }[]).slice(0, 200)
                );
            }
            const p = await VLC.getPlaylists();
            if (p.status === "ok" && p.value) {
                setPlaylists(p.value as { id: string; name: string }[]);
            }
            const state = await VLC.getCurrentState();
            if (state.status === "ok" && state.value?.currentTrack?.id) {
                setSeedId(state.value.currentTrack.id);
                setSeedTitle(state.value.currentTrack.title);
            }
        };
        load();
    }, []);

    const play = useCallback(
        async (collection: ICollectionPayload) => {
            const json = buildCollectionJson(collection);
            const ret = await VLC.playJukeboxCollection({
                collection: json,
                remote: playOnTv && remoteConnected,
            });
            if (ret.status === "error") {
                Toast.show({ text: ret.error });
            } else {
                navigate("/playing");
            }
        },
        [navigate, playOnTv, remoteConnected]
    );

    const decades = () => {
        const current = new Date().getFullYear();
        const items: { from: number; to: number; label: string }[] = [];
        for (let d = 1960; d <= current; d += 10) {
            items.push({
                from: d,
                to: Math.min(d + 9, current),
                label: `${d}s`,
            });
        }
        return items;
    };

    return (
        <div className="p-3 text-white">
            <h2>Jukebox</h2>
            <p className="text-secondary">
                Continuous playback by Collection — keeps going automatically.
            </p>
            {remoteConnected && (
                <div className="form-check form-switch mb-3">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={playOnTv}
                        onChange={(e) => setPlayOnTv(e.target.checked)}
                        id="playOnTv"
                    />
                    <label className="form-check-label" htmlFor="playOnTv">
                        Play on TV (Remote connected)
                    </label>
                </div>
            )}
            <div className="d-flex flex-wrap gap-2 mb-3">
                {(
                    [
                        "random",
                        "genre",
                        "artist",
                        "decade",
                        "similar",
                        "starred",
                        "server",
                    ] as JukeboxTab[]
                ).map((t) => (
                    <button
                        key={t}
                        className={`btn btn-sm ${tab === t ? "btn-primary" : "btn-outline-light"}`}
                        onClick={() => setTab(t)}
                    >
                        {t.charAt(0).toUpperCase() + t.slice(1)}
                    </button>
                ))}
            </div>
            {tab === "random" && (
                <button className="btn btn-primary" onClick={() => play({ type: "random" })}>
                    Play random mix
                </button>
            )}
            {tab === "genre" && (
                <div className="d-flex flex-column gap-2">
                    {genres.map((g) => (
                        <button
                            key={g.value}
                            className="btn btn-outline-light text-start"
                            onClick={() => play({ type: "genre", genre: g.value })}
                        >
                            {g.value}
                        </button>
                    ))}
                </div>
            )}
            {tab === "artist" && (
                <div className="d-flex flex-column gap-2">
                    {artists.map((a) => (
                        <button
                            key={a.id}
                            className="btn btn-outline-light text-start"
                            onClick={() =>
                                play({
                                    type: "artist",
                                    artistId: a.id,
                                    artistName: a.name,
                                })
                            }
                        >
                            {a.name}
                        </button>
                    ))}
                </div>
            )}
            {tab === "decade" && (
                <div className="d-flex flex-wrap gap-2">
                    {decades().map((d) => (
                        <button
                            key={d.label}
                            className="btn btn-outline-light"
                            onClick={() =>
                                play({
                                    type: "decade",
                                    fromYear: d.from,
                                    toYear: d.to,
                                })
                            }
                        >
                            {d.label}
                        </button>
                    ))}
                </div>
            )}
            {tab === "similar" && (
                <>
                    {!similarAvailable && (
                        <p className="text-warning">
                            Similar requires Last.fm configured on your Navidrome server.
                        </p>
                    )}
                    {seedId && seedTitle ? (
                        <button
                            className="btn btn-primary"
                            disabled={!similarAvailable}
                            onClick={() =>
                                play({
                                    type: "similar",
                                    seedSongId: seedId,
                                    seedTitle,
                                })
                            }
                        >
                            Similar to {seedTitle}
                        </button>
                    ) : (
                        <p>Play a song first to seed Similar.</p>
                    )}
                </>
            )}
            {tab === "starred" && (
                <button className="btn btn-primary" onClick={() => play({ type: "starred" })}>
                    Play starred songs
                </button>
            )}
            {tab === "server" && (
                <div className="d-flex flex-column gap-2">
                    {playlists.map((p) => (
                        <button
                            key={p.id}
                            className="btn btn-outline-light text-start"
                            onClick={() =>
                                play({
                                    type: "server",
                                    playlistId: p.id,
                                    playlistName: p.name,
                                })
                            }
                        >
                            {p.name}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
