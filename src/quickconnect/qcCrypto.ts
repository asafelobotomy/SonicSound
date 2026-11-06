import { p256 } from "@noble/curves/nist.js";
import { gcm } from "@noble/ciphers/aes.js";
import { hkdf } from "@noble/hashes/hkdf.js";
import { sha256 } from "@noble/hashes/sha2.js";
import { randomBytes } from "@noble/hashes/utils.js";

/**
 * Quick Connect crypto, mirrored in android/.../remote/QuickConnectCrypto.kt:
 * ECDH P-256 → HKDF-SHA256(salt = code, info = INFO) → AES-256-GCM.
 * Pure JS (noble) because the TV-served page is plain HTTP, where WebCrypto is unavailable.
 */
const INFO = "sonicsound-quickconnect-v1";
const enc = new TextEncoder();

export interface QuickConnectSealed {
    clientPub: string;
    nonce: string;
    ct: string;
}

export interface QuickConnectAccount {
    username: string;
    password: string;
    url: string;
    type: string;
    usePlaintext: boolean;
}

function toB64(bytes: Uint8Array): string {
    let s = "";
    bytes.forEach((b) => (s += String.fromCharCode(b)));
    return btoa(s);
}

function fromB64(b64: string): Uint8Array {
    const s = atob(b64);
    const out = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
    return out;
}

function deriveKey(secret: Uint8Array, peerPub: Uint8Array, code: string): Uint8Array {
    // getSharedSecret returns a compressed point; JCA ECDH yields only X.
    const sharedX = p256.getSharedSecret(secret, peerPub).slice(1);
    return hkdf(sha256, sharedX, enc.encode(code), enc.encode(INFO), 32);
}

/** Encrypts [plaintext] for the TV; optional fixed key/nonce exist only for test vectors. */
export function seal(
    tvPubB64: string,
    code: string,
    plaintext: string,
    fixed?: { clientSecret: Uint8Array; nonce: Uint8Array }
): QuickConnectSealed {
    const clientSecret = fixed?.clientSecret ?? p256.utils.randomSecretKey();
    const clientPub = p256.getPublicKey(clientSecret, false);
    const key = deriveKey(clientSecret, fromB64(tvPubB64), code);
    const nonce = fixed?.nonce ?? randomBytes(12);
    const ct = gcm(key, nonce).encrypt(enc.encode(plaintext));
    return { clientPub: toB64(clientPub), nonce: toB64(nonce), ct: toB64(ct) };
}

/** TV-side counterpart, used by tests to prove both directions agree. */
export function open(tvSecret: Uint8Array, code: string, sealed: QuickConnectSealed): string | null {
    try {
        const key = deriveKey(tvSecret, fromB64(sealed.clientPub), code);
        const pt = gcm(key, fromB64(sealed.nonce)).decrypt(fromB64(sealed.ct));
        return new TextDecoder().decode(pt);
    } catch {
        return null;
    }
}

export const _testing = { toB64, fromB64, p256 };
