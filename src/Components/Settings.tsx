import { Capacitor } from "@capacitor/core";
import { Toast } from "@capacitor/toast";
import { faGear } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import VLC, { ISettings } from "../Plugins/VLC";
import { pollToken, startDeviceAuth } from "../youtube/oauth";

const YT_PREMIUM = "https://www.youtube.com/premium";
const YT_OAUTH_DOCS =
    "https://developers.google.com/identity/protocols/oauth2/limited-input-device";

export default function Settings() {
    const [eqEnabled, setEqEnabled] = useState(false);
    const [replayGainEnabled, setReplayGainEnabled] = useState(false);
    const [youtubeVideosEnabled, setYoutubeVideosEnabled] = useState(false);
    const [youtubeAllowAnyChannel, setYoutubeAllowAnyChannel] = useState(false);
    const [offlineMode, setOfflineMode] = useState(false);
    const [artCacheLabel, setArtCacheLabel] = useState("Art cache: …");
    const [ytStatus, setYtStatus] = useState("YouTube: not signed in");
    const [ytUserCode, setYtUserCode] = useState("");
    const [oauthBusy, setOauthBusy] = useState(false);
    const pollCancel = useRef(false);
    const {
        register,
        handleSubmit,
        setValue,
        getValues,
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

    const refreshYtStatus = useCallback(async () => {
        const s = (await VLC.getSettings()).value;
        const signedIn = !!(
            s?.youtubeAccessToken || s?.youtubeRefreshToken
        );
        setYtStatus(signedIn ? "YouTube: signed in" : "YouTube: not signed in");
    }, []);

    useEffect(() => {
        const load = async () => {
            const settings = await VLC.getSettings();
            setValue("cacheSize", settings.value?.cacheSize ?? 0);
            setValue("transcoding", settings.value?.transcoding ?? "");
            setValue(
                "youtubeOauthClientId",
                settings.value?.youtubeOauthClientId ?? ""
            );
            setValue(
                "youtubeOauthClientSecret",
                settings.value?.youtubeOauthClientSecret ?? ""
            );
            setEqEnabled(settings.value?.eqEnabled ?? false);
            setReplayGainEnabled(settings.value?.replayGainEnabled ?? false);
            setYoutubeVideosEnabled(settings.value?.youtubeVideosEnabled ?? false);
            setYoutubeAllowAnyChannel(
                settings.value?.youtubeAllowAnyChannel ?? false
            );
            await refreshYtStatus();
            if (Capacitor.getPlatform() === "android") {
                setOfflineMode((await VLC.getOfflineMode()).value!);
                refreshArtCache();
            }
        };
        load();
        return () => {
            pollCancel.current = true;
        };
    }, [setValue, refreshArtCache, refreshYtStatus]);

    const signInYoutube = useCallback(async () => {
        await handleSubmit(save)();
        const clientId = getValues("youtubeOauthClientId")?.trim() ?? "";
        const clientSecret = getValues("youtubeOauthClientSecret")?.trim() ?? "";
        if (!clientId) {
            Toast.show({
                text: "Add a Google OAuth client ID (TVs and Limited Input).",
            });
            return;
        }
        setOauthBusy(true);
        pollCancel.current = false;
        try {
            const auth = await startDeviceAuth(clientId);
            setYtUserCode(`Enter code: ${auth.userCode}`);
            setYtStatus("Waiting for Google approval…");
            window.open(auth.verificationUrl, "_blank");
            const deadline = Date.now() + auth.expiresInSec * 1000;
            while (!pollCancel.current && Date.now() < deadline) {
                await new Promise((r) =>
                    setTimeout(r, auth.intervalSec * 1000)
                );
                const tokens = await pollToken(
                    clientId,
                    clientSecret,
                    auth.deviceCode
                );
                if (!tokens) continue;
                const current = (await VLC.getSettings()).value!;
                await VLC.setSettings({
                    ...current,
                    youtubeOauthClientId: clientId,
                    youtubeOauthClientSecret: clientSecret,
                    youtubeAccessToken: tokens.accessToken,
                    youtubeRefreshToken:
                        tokens.refreshToken ?? current.youtubeRefreshToken ?? "",
                    youtubeTokenExpiryMs:
                        Date.now() + tokens.expiresInSec * 1000,
                    youtubeVideosEnabled: true,
                });
                setYoutubeVideosEnabled(true);
                setYtUserCode("");
                setYtStatus("YouTube: signed in");
                Toast.show({ text: "YouTube signed in" });
                return;
            }
            if (!pollCancel.current) {
                Toast.show({ text: "YouTube sign-in timed out" });
                await refreshYtStatus();
            }
        } catch (e: unknown) {
            Toast.show({
                text: (e as Error)?.message ?? "YouTube sign-in failed",
            });
            await refreshYtStatus();
        } finally {
            setOauthBusy(false);
        }
    }, [getValues, handleSubmit, save, refreshYtStatus]);

    const signOutYoutube = useCallback(async () => {
        pollCancel.current = true;
        const current = (await VLC.getSettings()).value!;
        await VLC.setSettings({
            ...current,
            youtubeAccessToken: "",
            youtubeRefreshToken: "",
            youtubeTokenExpiryMs: 0,
        });
        setYtUserCode("");
        setYtStatus("YouTube: not signed in");
        Toast.show({ text: "YouTube signed out" });
    }, []);

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
                    {...register("youtubeOauthClientId")}
                    className="form-control mb-1"
                    placeholder="Google OAuth client ID (TV)"
                    autoComplete="off"
                />
                <input
                    {...register("youtubeOauthClientSecret")}
                    type="password"
                    className="form-control mb-1"
                    placeholder="OAuth client secret (optional)"
                    autoComplete="off"
                />
                <div className="subtitle text-white mb-1">{ytStatus}</div>
                {ytUserCode && (
                    <div className="text-white fw-bold mb-1">{ytUserCode}</div>
                )}
                <div className="d-flex flex-row gap-2 mb-2">
                    <button
                        type="button"
                        className="btn btn-outline-light btn-sm"
                        disabled={oauthBusy}
                        onClick={signInYoutube}
                    >
                        Sign in with Google
                    </button>
                    <button
                        type="button"
                        className="btn btn-outline-light btn-sm"
                        onClick={signOutYoutube}
                    >
                        Sign out
                    </button>
                </div>
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
                    On Android TV, Sign in uses the Google account on the device.
                    On web, use a TV OAuth client ID if prompted. See{" "}
                    <a href={YT_OAUTH_DOCS} target="_blank" rel="noreferrer">
                        device OAuth docs
                    </a>
                    .
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
