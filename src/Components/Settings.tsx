import { Capacitor } from "@capacitor/core";
import { Toast } from "@capacitor/toast";
import { faGear } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import VLC, { ISettings } from "../Plugins/VLC";

const PRIMARY_COLORS = [
    "#E53935",
    "#FB8C00",
    "#FDD835",
    "#43A047",
    "#1E88E5",
    "#8E24AA",
    "#00ACC1",
    "#D81B60",
    "#FFFFFF",
];

const VIZ_OPTIONS = [
    { value: "art_background", label: "Album art & background" },
    { value: "art_black", label: "Album art & no background" },
    { value: "art_solid", label: "Album art & solid color" },
    { value: "dvd", label: "DVD-style" },
    { value: "wmp_bars", label: "WMP: Bars" },
    { value: "wmp_scope", label: "WMP: Scope" },
    { value: "wmp_ocean_mist", label: "WMP: Ocean Mist" },
    { value: "wmp_fire_storm", label: "WMP: Fire Storm" },
    { value: "wmp_battery", label: "WMP: Battery" },
    { value: "wmp_alchemy", label: "WMP: Alchemy" },
    { value: "wmp_ambience", label: "WMP: Ambience" },
    { value: "wmp_particle", label: "WMP: Particle" },
    { value: "wmp_plenoptic", label: "WMP: Plenoptic" },
    { value: "wmp_spikes", label: "WMP: Spikes" },
    { value: "wmp_musical_colors", label: "WMP: Musical Colors" },
    { value: "wmp_blazing_colors", label: "WMP: Blazing Colors" },
    { value: "wmp_color_cubes", label: "WMP: Color Cubes" },
    { value: "wmp_pulsing_colors", label: "WMP: Pulsing Colors" },
    { value: "wmp_startime", label: "WMP: StarTime" },
    { value: "wmp_snowtime", label: "WMP: SnowTime" },
] as const;

const DVD_SPEEDS = [
    { value: "slow", label: "Slow" },
    { value: "default", label: "Default" },
    { value: "fast", label: "Fast" },
] as const;

const AUDIO_PROFILES = [
    { value: "off", label: "Off" },
    { value: "flat", label: "Flat" },
    { value: "bass", label: "Bass boost" },
    { value: "treble", label: "Treble boost" },
    { value: "vocal", label: "Vocal / speech" },
    { value: "rock", label: "Rock" },
    { value: "electronic", label: "Electronic" },
    { value: "classical", label: "Classical" },
    { value: "pop", label: "Pop" },
    { value: "tv", label: "TV / living room" },
    { value: "headphones", label: "Headphones" },
] as const;

export default function Settings() {
    const [audioProfile, setAudioProfile] = useState("off");
    const [replayGainEnabled, setReplayGainEnabled] = useState(false);
    const [offlineMode, setOfflineMode] = useState(false);
    const [artCacheLabel, setArtCacheLabel] = useState("Art cache: …");
    const [visualizer, setVisualizer] = useState<string>("art_background");
    const [solidColor, setSolidColor] = useState("#E53935");
    const [dvdSpeed, setDvdSpeed] = useState("default");
    const [showClock, setShowClock] = useState(false);
    const [showDate, setShowDate] = useState(false);
    const readyRef = useRef(false);
    const textSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isAndroid = Capacitor.getPlatform() === "android";
    const {
        register,
        setValue,
        getValues,
        formState: { errors },
    } = useForm<ISettings>();

    const persist = useCallback(
        async (patch: Partial<ISettings> = {}) => {
            if (!readyRef.current) return;
            const current = (await VLC.getSettings()).value;
            const form = getValues();
            const ret = await VLC.setSettings({
                ...current,
                ...form,
                audioProfile,
                eqEnabled: audioProfile !== "off",
                replayGainEnabled,
                fullscreenVisualizer: visualizer,
                fullscreenSolidColor: solidColor,
                dvdSpeed,
                fullscreenShowClock: showClock,
                fullscreenShowDate: showDate,
                ...patch,
            });
            if (ret.status !== "ok") {
                Toast.show({ text: ret.error });
            }
        },
        [
            audioProfile,
            replayGainEnabled,
            visualizer,
            solidColor,
            dvdSpeed,
            showClock,
            showDate,
            getValues,
        ]
    );

    const scheduleTextPersist = useCallback(() => {
        if (textSaveTimer.current) clearTimeout(textSaveTimer.current);
        textSaveTimer.current = setTimeout(() => {
            persist();
        }, 400);
    }, [persist]);

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
            const profile =
                settings.value?.audioProfile?.trim() ||
                (settings.value?.eqEnabled ? "flat" : "off");
            setAudioProfile(profile);
            setReplayGainEnabled(settings.value?.replayGainEnabled ?? false);
            setVisualizer(settings.value?.fullscreenVisualizer ?? "art_background");
            setSolidColor(settings.value?.fullscreenSolidColor ?? "#E53935");
            setDvdSpeed(settings.value?.dvdSpeed ?? "default");
            setShowClock(settings.value?.fullscreenShowClock ?? false);
            setShowDate(settings.value?.fullscreenShowDate ?? false);
            if (isAndroid) {
                setOfflineMode((await VLC.getOfflineMode()).value!);
                refreshArtCache();
            }
            readyRef.current = true;
        };
        load();
        return () => {
            if (textSaveTimer.current) clearTimeout(textSaveTimer.current);
        };
    }, [setValue, refreshArtCache, isAndroid]);

    return (
        <div className="d-flex flex-column align-items-start overflow-scroll scrollable p-3 w-100">
            <div className="d-flex flex-row align-items-center mb-3">
                <FontAwesomeIcon icon={faGear} size="2x" className="text-white me-3" />
                <div className="section-header text-white mb-0">Settings</div>
            </div>
            <form className="w-100" style={{ maxWidth: 520 }} onSubmit={(e) => e.preventDefault()}>
                <div className="section-header text-white">Playback</div>
                <label className="subtitle text-white-50 mb-1 d-block">
                    Transcoding format
                </label>
                <input
                    {...register("transcoding", {
                        onChange: () => scheduleTextPersist(),
                    })}
                    className="form-control mb-3"
                    placeholder="e.g. mp3, raw (leave blank for server default)"
                />

                <div className="section-header text-white">Audio</div>
                <label className="subtitle text-white-50 mb-1 d-block">
                    Audio profile {isAndroid ? "" : "(Android playback only)"}
                </label>
                <select
                    className="form-select mb-2"
                    value={audioProfile}
                    disabled={!isAndroid}
                    onChange={(e) => {
                        const next = e.target.value;
                        setAudioProfile(next);
                        persist({ audioProfile: next, eqEnabled: next !== "off" });
                    }}
                >
                    {AUDIO_PROFILES.map((p) => (
                        <option key={p.value} value={p.value}>
                            {p.label}
                        </option>
                    ))}
                </select>
                <div className="form-check form-switch mb-3">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={replayGainEnabled}
                        disabled={!isAndroid}
                        onChange={() => {
                            const next = !replayGainEnabled;
                            setReplayGainEnabled(next);
                            persist({ replayGainEnabled: next });
                        }}
                        id="rgSwitch"
                    />
                    <label className="form-check-label text-white" htmlFor="rgSwitch">
                        ReplayGain
                    </label>
                </div>

                <div className="section-header text-white">Fullscreen</div>
                <label className="subtitle text-white-50 mb-1 d-block">Visualizer</label>
                <select
                    className="form-select mb-2"
                    value={visualizer}
                    onChange={(e) => {
                        const next = e.target.value;
                        setVisualizer(next);
                        persist({ fullscreenVisualizer: next });
                    }}
                >
                    {VIZ_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                            {o.label}
                        </option>
                    ))}
                </select>
                {visualizer === "art_solid" && (
                    <div className="mb-2">
                        <label className="subtitle text-white-50 mb-1 d-block">
                            Solid color
                        </label>
                        <div className="d-flex flex-row flex-wrap gap-2">
                            {PRIMARY_COLORS.map((hex) => (
                                <button
                                    key={hex}
                                    type="button"
                                    aria-label={hex}
                                    onClick={() => {
                                        setSolidColor(hex);
                                        persist({ fullscreenSolidColor: hex });
                                    }}
                                    style={{
                                        width: 36,
                                        height: 36,
                                        borderRadius: "50%",
                                        background: hex,
                                        border:
                                            solidColor.toLowerCase() === hex.toLowerCase()
                                                ? "3px solid #fff"
                                                : "1px solid rgba(255,255,255,0.4)",
                                        padding: 0,
                                    }}
                                />
                            ))}
                        </div>
                    </div>
                )}
                {visualizer === "dvd" && (
                    <>
                        <label className="subtitle text-white-50 mb-1 d-block">
                            DVD speed
                        </label>
                        <select
                            className="form-select mb-2"
                            value={dvdSpeed}
                            onChange={(e) => {
                                const next = e.target.value;
                                setDvdSpeed(next);
                                persist({ dvdSpeed: next });
                            }}
                        >
                            {DVD_SPEEDS.map((o) => (
                                <option key={o.value} value={o.value}>
                                    {o.label}
                                </option>
                            ))}
                        </select>
                    </>
                )}
                <div className="form-check form-switch mb-2">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={showClock}
                        onChange={() => {
                            const next = !showClock;
                            setShowClock(next);
                            persist({ fullscreenShowClock: next });
                        }}
                        id="fsClock"
                    />
                    <label className="form-check-label text-white" htmlFor="fsClock">
                        Show current time
                    </label>
                </div>
                <div className="form-check form-switch mb-3">
                    <input
                        className="form-check-input"
                        type="checkbox"
                        checked={showDate}
                        onChange={() => {
                            const next = !showDate;
                            setShowDate(next);
                            persist({ fullscreenShowDate: next });
                        }}
                        id="fsDate"
                    />
                    <label className="form-check-label text-white" htmlFor="fsDate">
                        Show current date
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
                                onChange: () => scheduleTextPersist(),
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
            </form>
        </div>
    );
}
