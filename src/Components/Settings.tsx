import { Capacitor } from "@capacitor/core";
import { Toast } from "@capacitor/toast";
import { faGear } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import VLC, { ISettings } from "../Plugins/VLC";

const YT_PREMIUM = "https://www.youtube.com/premium";
const YT_API_DOCS =
    "https://developers.google.com/youtube/v3/getting-started";

export default function Settings() {
    const [eqEnabled, setEqEnabled] = useState(false);
    const [replayGainEnabled, setReplayGainEnabled] = useState(false);
    const [youtubeVideosEnabled, setYoutubeVideosEnabled] = useState(false);
    const [youtubeAllowAnyChannel, setYoutubeAllowAnyChannel] = useState(false);
    const [offlineMode, setOfflineMode] = useState(false);
    const [artCacheLabel, setArtCacheLabel] = useState("Art cache: …");
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
            const ret = await VLC.setSettings({
                ...data,
                eqEnabled,
                replayGainEnabled,
                youtubeVideosEnabled,
                youtubeAllowAnyChannel,
            });
            if (ret.status === "ok") {
                Toast.show({ text: "Settings saved" });
            } else {
                Toast.show({ text: ret.error });
            }
        },
        [eqEnabled, replayGainEnabled, youtubeVideosEnabled, youtubeAllowAnyChannel]
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

    useEffect(() => {
        const load = async () => {
            const settings = await VLC.getSettings();
            setValue("cacheSize", settings.value?.cacheSize ?? 0);
            setValue("transcoding", settings.value?.transcoding ?? "");
            setValue("youtubeApiKey", settings.value?.youtubeApiKey ?? "");
            setEqEnabled(settings.value?.eqEnabled ?? false);
            setReplayGainEnabled(settings.value?.replayGainEnabled ?? false);
            setYoutubeVideosEnabled(settings.value?.youtubeVideosEnabled ?? false);
            setYoutubeAllowAnyChannel(
                settings.value?.youtubeAllowAnyChannel ?? false
            );
            if (Capacitor.getPlatform() === "android") {
                setOfflineMode((await VLC.getOfflineMode()).value!);
                refreshArtCache();
            }
        };
        load();
    }, [setValue, refreshArtCache]);

    return (
        <div className="d-flex flex-column align-items-center overflow-scroll scrollable p-3">
            <FontAwesomeIcon icon={faGear} size="3x" className="text-white mb-2" />
            <div className="section-header text-white mb-3">Settings</div>
            <form className="w-100" onSubmit={handleSubmit(save)}>
                <div className="section-header text-white">Transcoding</div>
                <input
                    {...register("transcoding")}
                    className="form-control mb-2"
                    placeholder="Transcoding format"
                />
                <div className="section-header text-white mt-3">Audio</div>
                <div className="form-check form-switch">
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
                <div className="form-check form-switch mb-2">
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
                <div className="section-header text-white mt-3">YouTube music videos</div>
                <div className="form-check form-switch">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={youtubeVideosEnabled}
                        onChange={() =>
                            setYoutubeVideosEnabled(!youtubeVideosEnabled)
                        }
                        id="ytSwitch"
                    />
                    <label className="form-check-label text-white" htmlFor="ytSwitch">
                        Enable music video search
                    </label>
                </div>
                <input
                    {...register("youtubeApiKey")}
                    type="password"
                    className="form-control mb-1"
                    placeholder="YouTube Data API key"
                    autoComplete="off"
                />
                <div className="form-check form-switch mb-2">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={youtubeAllowAnyChannel}
                        onChange={() =>
                            setYoutubeAllowAnyChannel(!youtubeAllowAnyChannel)
                        }
                        id="ytAnySwitch"
                    />
                    <label
                        className="form-check-label text-white"
                        htmlFor="ytAnySwitch"
                    >
                        Allow any YouTube channel
                    </label>
                </div>
                <div className="subtitle text-white mb-2">
                    Off (default): only VEVO, artist, and Official channels. On:
                    any channel if the title matches the song.
                </div>
                <div className="subtitle text-white mb-2">
                    Official{" "}
                    <a href={YT_API_DOCS} target="_blank" rel="noreferrer">
                        YouTube Data API v3
                    </a>{" "}
                    only. In Now Playing, music videos play YouTube audio (server
                    muted) and follow the play queue.{" "}
                    <a href={YT_PREMIUM} target="_blank" rel="noreferrer">
                        YouTube Premium
                    </a>{" "}
                    removes ads when signed in.
                </div>
                <a
                    className="btn btn-outline-light btn-sm mb-3"
                    href={YT_PREMIUM}
                    target="_blank"
                    rel="noreferrer"
                >
                    Open YouTube Premium
                </a>
                {Capacitor.getPlatform() === "android" && (
                    <>
                        <div className="section-header text-white">Cache</div>
                        <input
                            {...register("cacheSize", { min: 0 })}
                            type="number"
                            className="form-control mb-1"
                            placeholder="Cache size (GB)"
                        />
                        {errors.cacheSize && (
                            <div className="subtitle text-danger">
                                {errors.cacheSize.message}
                            </div>
                        )}
                        <div className="subtitle text-white">{artCacheLabel}</div>
                        <div className="form-check form-switch mt-2">
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
                <div className="d-flex justify-content-end mt-3">
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
