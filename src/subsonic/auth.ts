import md5 from "js-md5";
import { IBasicParams } from "../Models/API/Requests/BasicParams";
import { IAccount } from "../Models/AppContext";

export const CLIENT_NAME = "sonicsound";
export const API_VERSION_CANDIDATES = [
    "1.16.1",
    "1.15.0",
    "1.13.0",
    "1.12.0",
] as const;

export type ApiVersion = (typeof API_VERSION_CANDIDATES)[number];

/** Cryptographically-strong-enough random salt for Subsonic token auth. */
export function randomSalt(length = 16): string {
    const alphabet =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    const bytes = new Uint8Array(length);
    if (typeof crypto !== "undefined" && crypto.getRandomValues) {
        crypto.getRandomValues(bytes);
    } else {
        for (let i = 0; i < length; i++) {
            bytes[i] = Math.floor(Math.random() * 256);
        }
    }
    let out = "";
    for (let i = 0; i < length; i++) {
        out += alphabet[bytes[i] % alphabet.length];
    }
    return out;
}

export function buildAuthParams(
    account: Pick<IAccount, "username" | "password" | "usePlaintext">,
    apiVersion: string = API_VERSION_CANDIDATES[0]
): IBasicParams {
    const salt = randomSalt();
    const usePlain = !!account.usePlaintext;
    const hash = md5(`${account.password}${salt}`);
    return {
        u: account.username!,
        t: usePlain ? undefined : hash,
        s: usePlain ? undefined : salt,
        v: apiVersion,
        c: CLIENT_NAME,
        f: "json",
        p: usePlain ? account.password : undefined,
    };
}

export function isVersionRejectedMessage(message: string | undefined): boolean {
    if (!message) return false;
    const m = message.toLowerCase();
    return (
        m.includes("incompatible") ||
        m.includes("protocol version") ||
        m.includes("upgrade") ||
        m.includes("api version")
    );
}
