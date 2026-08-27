import { Capacitor } from "@capacitor/core";
import { Toast } from "@capacitor/toast";
import { faGear } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import VLC, { ISettings } from "../Plugins/VLC";

export default function Settings() {
    const [eqEnabled, setEqEnabled] = useState(false);
    const [replayGainEnabled, setReplayGainEnabled] = useState(false);
    const [offlineMode, setOfflineMode] = useState(false);
    const [artCacheLabel, setArtCacheLabel] = useState("Art cache: …");
    const isAndroid = Capacitor.getPlatform() === "android";
    const {
        register,
        handleSubmit,
        setValue,
        formState: { errors },
    } = useForm<ISettings>();
    const { focused: saveFocused, ref: saveRef } = useFocusable({
        onEnterPress: () => handleSubmit(save)(),
    });

    const save = useCallback(
        async (data: ISettings) => {
            const current = (await VLC.getSettings()).value;
            const ret = await VLC.setSettings({
                ...current,
                ...data,
                eqEnabled,
                replayGainEnabled,
            });
            if (ret.status === "ok") {
                Toast.show({ text: "Settings saved" });
            } else {
                Toast.show({ text: ret.error });
            }
        },
        [eqEnabled, replayGainEnabled]
    );

    const refreshArtCache = useCallback(async () => {
        try {
            const ret = await VLC.getCoverCacheSize();
            if (ret.status === "ok" && ret.value) {
                setArtCacheLabel(
                    `Art cache: ${Math.round(ret.value.bytes / 1024)} KB`
                );
            }
        } catch {
            setArtCacheLabel("Art cache: unavailable");
        }
    }, []);

    const clearArtCache = useCallback(async () => {
        try {
            const ret = await VLC.clearCoverCache();
            if (ret.status === "ok" && ret.value) {
                Toast.show({
                    text: `Cleared ${Math.round(ret.value.freedBytes / 1024)} KB of cached art`,
                });
            } else {
                Toast.show({ text: ret.error || "Could not clear cache" });
            }
            await refreshArtCache();
        } catch {
            Toast.show({ text: "Could not clear cache" });
        }
    }, [refreshArtCache]);

    useEffect(() => {
        const load = async () => {
            const settings = await VLC.getSettings();
            setValue("cacheSize", settings.value?.cacheSize ?? 0);
            setValue("transcoding", settings.value?.transcoding ?? "");
            setEqEnabled(settings.value?.eqEnabled ?? false);
            setReplayGainEnabled(settings.value?.replayGainEnabled ?? false);
            if (isAndroid) {
                setOfflineMode((await VLC.getOfflineMode()).value!);
                refreshArtCache();
            }
        };
        load();
    }, [setValue, refreshArtCache, isAndroid]);

    return (
        <div className="d-flex flex-column align-items-start overflow-scroll scrollable p-3 w-100">
            <div className="d-flex flex-row align-items-center mb-3">
                <FontAwesomeIcon icon={faGear} size="2x" className="text-white me-3" />
                <div className="section-header text-white mb-0">Settings</div>
            </div>
            <form className="w-100" style={{ maxWidth: 520 }} onSubmit={handleSubmit(save)}>
                <div className="section-header text-white">Playback</div>
                <label className="subtitle text-white-50 mb-1 d-block">
                    Transcoding format
                </label>
                <input
                    {...register("transcoding")}
                    className="form-control mb-3"
                    placeholder="e.g. mp3, raw (leave blank for server default)"
                />

                <div className="section-header text-white">Audio</div>
                <div className="form-check form-switch mb-2">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={eqEnabled}
                        onChange={() => setEqEnabled(!eqEnabled)}
                        id="eqSwitch"
                    />
                    <label className="form-check-label text-white" htmlFor="eqSwitch">
                        Audio equalizer
                    </label>
                </div>
                <div className="form-check form-switch mb-3">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={replayGainEnabled}
                        onChange={() => setReplayGainEnabled(!replayGainEnabled)}
                        id="rgSwitch"
                    />
                    <label className="form-check-label text-white" htmlFor="rgSwitch">
                        ReplayGain
                    </label>
                </div>

                {isAndroid && (
                    <>
                        <div className="section-header text-white">Cache</div>
                        <label className="subtitle text-white-50 mb-1 d-block">
                            Cache size (GB)
                        </label>
                        <input
                            {...register("cacheSize", {
                                valueAsNumber: true,
                                min: 0,
                            })}
                            type="number"
                            className="form-control mb-1"
                            placeholder="Cache size (GB)"
                        />
                        {errors.cacheSize && (
                            <div className="subtitle text-danger">
                                {errors.cacheSize.message}
                            </div>
                        )}
                        <div className="subtitle text-white mb-2">{artCacheLabel}</div>
                        <button
                            type="button"
                            className="btn btn-outline-light btn-sm mb-3"
                            onClick={clearArtCache}
                        >
                            Clear art cache
                        </button>
                        <div className="form-check form-switch mb-3">
                            <input
                                className="form-check-input"
                                type="checkbox"
                                checked={offlineMode}
                                onChange={async () => {
                                    const v = (
                                        await VLC.setOfflineMode({
                                            value: !offlineMode,
                                        })
                                    ).value;
                                    setOfflineMode(v!);
                                }}
                                id="offlineSwitch"
                            />
                            <label
                                className="form-check-label text-white"
                                htmlFor="offlineSwitch"
                            >
                                Offline mode
                            </label>
                        </div>
                    </>
                )}

                <div className="d-flex justify-content-start mt-2">
                    <button
                        ref={saveRef}
                        className={classNames(
                            "btn",
                            saveFocused ? "btn-selected" : "btn-primary"
                        )}
                    >
                        Save settings
                    </button>
                </div>
            </form>
        </div>
    );
}
