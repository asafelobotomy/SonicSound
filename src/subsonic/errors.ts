import { IBackendResponse } from "../Plugins/VLC";

export function okResponse<T>(value: T): IBackendResponse<T> {
    return { status: "ok", error: "", value };
}

export function errorResponse<T = string>(error?: string): IBackendResponse<T> {
    return {
        status: "error",
        error: error ?? "There was an error.",
        value: null,
    };
}

export type SubsonicErrorKind =
    | "protocol"
    | "auth"
    | "network"
    | "tls"
    | "unknown";

export function classifySubsonicError(
    message: string | undefined
): SubsonicErrorKind {
    if (!message) return "unknown";
    const m = message.toLowerCase();
    if (
        m.includes("incompatible") ||
        m.includes("protocol") ||
        m.includes("upgrade")
    ) {
        return "protocol";
    }
    if (
        m.includes("wrong username") ||
        m.includes("password") ||
        m.includes("unauthorized") ||
        m.includes("authentication")
    ) {
        return "auth";
    }
    if (m.includes("ssl") || m.includes("tls") || m.includes("certificate")) {
        return "tls";
    }
    if (
        m.includes("network") ||
        m.includes("timeout") ||
        m.includes("econn") ||
        m.includes("connecting")
    ) {
        return "network";
    }
    return "unknown";
}
