import { Toast } from "@capacitor/toast";
import { useCallback, useEffect, useState } from "react";
import VLC from "../Plugins/VLC";
import { IInternetRadioStation } from "../subsonic/endpoints/library";

export default function Radio() {
    const [stations, setStations] = useState<IInternetRadioStation[]>([]);
    const [loading, setLoading] = useState(true);

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

    return (
        <div className="d-flex flex-column w-100 h-100 align-items-start playlist-container">
            <div className="section-header text-white">Internet Radio</div>
            <hr className="text-white w-100 mt-0" />
            {loading && <div className="text-white">Loading…</div>}
            {!loading && stations.length === 0 && (
                <div className="text-white-50">
                    No radio stations configured on the server.
                </div>
            )}
            <div className="list-group w-100">
                {stations.map((station) => (
                    <button
                        key={station.id}
                        type="button"
                        className="list-group-item list-group-item-action text-start"
                        onClick={() => play(station)}
                    >
                        <div className="fw-bold">{station.name}</div>
                        <div className="small text-muted">
                            {station.homePageUrl || station.streamUrl}
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
}
