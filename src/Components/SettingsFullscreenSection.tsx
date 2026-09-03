import { ISettings } from "../Plugins/VLC";

export const PRIMARY_COLORS = [
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

export const VIZ_OPTIONS = [
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

export const DVD_SPEEDS = [
    { value: "slow", label: "Slow" },
    { value: "default", label: "Default" },
    { value: "fast", label: "Fast" },
] as const;

type PersistFn = (patch?: Partial<ISettings>) => void;

export function SettingsFullscreenSection(props: {
    visualizer: string;
    solidColor: string;
    dvdSpeed: string;
    showClock: boolean;
    showDate: boolean;
    setVisualizer: (v: string) => void;
    setSolidColor: (v: string) => void;
    setDvdSpeed: (v: string) => void;
    setShowClock: (v: boolean) => void;
    setShowDate: (v: boolean) => void;
    persist: PersistFn;
}) {
    const {
        visualizer,
        solidColor,
        dvdSpeed,
        showClock,
        showDate,
        setVisualizer,
        setSolidColor,
        setDvdSpeed,
        setShowClock,
        setShowDate,
        persist,
    } = props;

    return (
        <>
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
        </>
    );
}
