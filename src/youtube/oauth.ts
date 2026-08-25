/** Google OAuth 2.0 device-code flow for YouTube Data API (TV / limited input). */

export const YT_OAUTH_SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

export type DeviceAuth = {
    deviceCode: string;
    userCode: string;
    verificationUrl: string;
    intervalSec: number;
    expiresInSec: number;
};

export type OauthTokens = {
    accessToken: string;
    refreshToken?: string;
    expiresInSec: number;
};

async function postForm(
    url: string,
    params: Record<string, string>
): Promise<Record<string, unknown>> {
    const body = new URLSearchParams(params).toString();
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
    });
    return (await res.json()) as Record<string, unknown>;
}

export async function startDeviceAuth(clientId: string): Promise<DeviceAuth> {
    const json = await postForm("https://oauth2.googleapis.com/device/code", {
        client_id: clientId,
        scope: YT_OAUTH_SCOPE,
    });
    if (json.error) {
        throw new Error(String(json.error_description ?? json.error));
    }
    return {
        deviceCode: String(json.device_code),
        userCode: String(json.user_code),
        verificationUrl: String(
            json.verification_url ?? "https://www.google.com/device"
        ),
        intervalSec: Math.max(3, Number(json.interval ?? 5)),
        expiresInSec: Number(json.expires_in ?? 1800),
    };
}

export async function pollToken(
    clientId: string,
    clientSecret: string,
    deviceCode: string
): Promise<OauthTokens | null> {
    const params: Record<string, string> = {
        client_id: clientId,
        device_code: deviceCode,
        grant_type: "urn:ietf:params:oauth:grant-type:device_code",
    };
    if (clientSecret) params.client_secret = clientSecret;
    const json = await postForm("https://oauth2.googleapis.com/token", params);
    if (json.error) {
        const err = String(json.error);
        if (err === "authorization_pending" || err === "slow_down") return null;
        throw new Error(String(json.error_description ?? err));
    }
    return {
        accessToken: String(json.access_token),
        refreshToken: json.refresh_token
            ? String(json.refresh_token)
            : undefined,
        expiresInSec: Number(json.expires_in ?? 3600),
    };
}

export async function refreshAccessToken(
    clientId: string,
    clientSecret: string,
    refreshToken: string
): Promise<OauthTokens> {
    const params: Record<string, string> = {
        client_id: clientId,
        refresh_token: refreshToken,
        grant_type: "refresh_token",
    };
    if (clientSecret) params.client_secret = clientSecret;
    const json = await postForm("https://oauth2.googleapis.com/token", params);
    if (json.error) {
        throw new Error(String(json.error_description ?? json.error));
    }
    return {
        accessToken: String(json.access_token),
        refreshToken,
        expiresInSec: Number(json.expires_in ?? 3600),
    };
}

export async function validAccessToken(settings: {
    youtubeAccessToken?: string;
    youtubeRefreshToken?: string;
    youtubeTokenExpiryMs?: number;
    youtubeOauthClientId?: string;
    youtubeOauthClientSecret?: string;
}): Promise<string> {
    const access = settings.youtubeAccessToken ?? "";
    if (!access) return "";
    const expiry = settings.youtubeTokenExpiryMs ?? 0;
    if (Date.now() < expiry - 60_000) return access;
    const refresh = settings.youtubeRefreshToken ?? "";
    const clientId = settings.youtubeOauthClientId ?? "";
    if (!refresh || !clientId) return access;
    try {
        const t = await refreshAccessToken(
            clientId,
            settings.youtubeOauthClientSecret ?? "",
            refresh
        );
        return t.accessToken;
    } catch {
        return access;
    }
}
