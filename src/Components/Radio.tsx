import { Toast } from "@capacitor/toast";
import { useCallback, useEffect, useState } from "react";
import VLC from "../Plugins/VLC";
import { IInternetRadioStation } from "../subsonic/endpoints/library";
import TVActionButton from "./TVActionButton";

const emptyForm = { name: "", streamUrl: "", homePageUrl: "" };

export default function Radio() {
    const [stations, setStations] = useState<IInternetRadioStation[]>([]);
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState(emptyForm);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [showForm, setShowForm] = useState(false);

    const fetch = useCallback(async () => {
        setLoading(true);
        const ret = await VLC.getInternetRadioStations();
        if (ret.status === "ok") {
            setStations(ret.value ?? []);
        } else {
            Toast.show({ text: ret.error });
        }
        setLoading(false);
    }, []);

    useEffect(() => {
        fetch();
    }, [fetch]);

    const play = async (station: IInternetRadioStation) => {
        const ret = await VLC.playInternetRadio({
            streamUrl: station.streamUrl,
            name: station.name,
        });
        if (ret.status !== "ok") {
            Toast.show({ text: ret.error });
        }
    };

    const startCreate = () => {
        setEditingId(null);
        setForm(emptyForm);
        setShowForm(true);
    };

    const startEdit = (station: IInternetRadioStation) => {
        setEditingId(station.id);
        setForm({
            name: station.name,
            streamUrl: station.streamUrl,
            homePageUrl: station.homePageUrl ?? "",
        });
        setShowForm(true);
    };

    const saveStation = async () => {
        if (!form.name.trim() || !form.streamUrl.trim()) {
            Toast.show({ text: "Name and stream URL are required" });
            return;
        }
        const payload = {
            name: form.name.trim(),
            streamUrl: form.streamUrl.trim(),
            homePageUrl: form.homePageUrl.trim() || undefined,
        };
        const ret = editingId
            ? await VLC.updateInternetRadioStation({ id: editingId, ...payload })
            : await VLC.createInternetRadioStation(payload);
        if (ret.status === "ok") {
            Toast.show({
                text: editingId ? "Station saved" : "Station added",
            });
            setShowForm(false);
            setForm(emptyForm);
            setEditingId(null);
            await fetch();
        } else {
            const msg = ret.error.toLowerCase().includes("admin")
                ? "Admin account required to manage radio stations"
                : ret.error;
            Toast.show({ text: msg });
        }
    };

    const deleteStation = async (station: IInternetRadioStation) => {
        const ret = await VLC.deleteInternetRadioStation({ id: station.id });
        if (ret.status === "ok") {
            Toast.show({ text: "Station deleted" });
            await fetch();
        } else {
            const msg = ret.error.toLowerCase().includes("admin")
                ? "Admin account required to manage radio stations"
                : ret.error;
            Toast.show({ text: msg });
        }
    };

    return (
        <div className="d-flex flex-column w-100 h-100 align-items-start playlist-container">
            <div className="d-flex flex-row align-items-center justify-content-between w-100 mb-2">
                <div className="section-header text-white mb-0">Internet Radio</div>
                <TVActionButton content="Add station" func={startCreate} />
            </div>
            <hr className="text-white w-100 mt-0" />
            {showForm && (
                <div className="w-100 mb-3" style={{ maxWidth: 520 }}>
                    <div className="section-header text-white">
                        {editingId ? "Edit station" : "New station"}
                    </div>
                    <input
                        className="form-control mb-2"
                        placeholder="Station name"
                        value={form.name}
                        onChange={(e) =>
                            setForm({ ...form, name: e.target.value })
                        }
                    />
                    <input
                        className="form-control mb-2"
                        placeholder="Stream URL"
                        value={form.streamUrl}
                        onChange={(e) =>
                            setForm({ ...form, streamUrl: e.target.value })
                        }
                    />
                    <input
                        className="form-control mb-2"
                        placeholder="Home page URL (optional)"
                        value={form.homePageUrl}
                        onChange={(e) =>
                            setForm({ ...form, homePageUrl: e.target.value })
                        }
                    />
                    <div className="d-flex flex-row gap-2">
                        <TVActionButton content="Save" func={saveStation} />
                        <TVActionButton
                            content="Cancel"
                            func={() => {
                                setShowForm(false);
                                setEditingId(null);
                                setForm(emptyForm);
                            }}
                        />
                    </div>
                    <div className="subtitle text-white-50 mt-2">
                        Managing stations requires a Navidrome admin account.
                    </div>
                </div>
            )}
            {loading && <div className="text-white">Loading…</div>}
            {!loading && stations.length === 0 && (
                <div className="text-white-50">
                    No radio stations yet. Add one with an admin account.
                </div>
            )}
            <div className="list-group w-100">
                {stations.map((station) => (
                    <div
                        key={station.id}
                        className="list-group-item list-group-item-action d-flex flex-row align-items-center justify-content-between gap-2"
                    >
                        <button
                            type="button"
                            className="btn btn-link text-start text-white flex-grow-1 p-0"
                            onClick={() => play(station)}
                        >
                            <div className="fw-bold">{station.name}</div>
                            <div className="small text-muted">
                                {station.homePageUrl || station.streamUrl}
                            </div>
                        </button>
                        <div className="d-flex flex-row gap-2">
                            <TVActionButton
                                content="Edit"
                                func={() => startEdit(station)}
                            />
                            <TVActionButton
                                content="Delete"
                                func={() => deleteStation(station)}
                            />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
