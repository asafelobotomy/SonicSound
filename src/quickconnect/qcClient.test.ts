import { describe, expect, it } from "vitest";
import { runQuickConnect } from "./qcClient";
import { _testing, open, seal } from "./qcCrypto";

const { p256, toB64 } = _testing;

const account = { username: "u", password: "p", url: "http://s", type: "", usePlaintext: false };

/** In-memory stand-in for QuickConnectHttpServer.kt's /api/qc routes. */
function tvWithCode(code: string) {
    const secret = p256.utils.randomSecretKey();
    const tvPub = toB64(p256.getPublicKey(secret, false));
    let received: string | null = null;
    const reply = (status: string, data: string) =>
        ({ json: async () => ({ status, data }) }) as Response;
    const fetchImpl = (async (url: string, init?: RequestInit) => {
        if (url.endsWith("/api/qc/hello")) return reply("ok", JSON.stringify({ tvPub }));
        received = open(secret, code, JSON.parse(String(init?.body)));
        return received
            ? reply("ok", "Signed in on the TV.")
            : reply("error", "Wrong code. 4 attempts left.");
    }) as typeof fetch;
    return { fetchImpl, received: () => received };
}

describe("runQuickConnect", () => {
    it("delivers the encrypted account and reports success", async () => {
        const tv = tvWithCode("482913");
        const res = await runQuickConnect({
            baseUrl: "http://tv",
            code: "482913",
            account,
            seal,
            fetchImpl: tv.fetchImpl,
        });
        expect(res).toEqual({ ok: true, message: "Signed in on the TV." });
        expect(JSON.parse(tv.received()!)).toEqual(account);
    });

    it("surfaces a wrong code", async () => {
        const tv = tvWithCode("482913");
        const res = await runQuickConnect({
            baseUrl: "http://tv",
            code: "111111",
            account,
            seal,
            fetchImpl: tv.fetchImpl,
        });
        expect(res.ok).toBe(false);
        expect(res.message).toMatch(/Wrong code/);
    });

    it("reports an unreachable TV", async () => {
        const res = await runQuickConnect({
            baseUrl: "http://tv",
            code: "482913",
            account,
            seal,
            fetchImpl: (async () => {
                throw new TypeError("network");
            }) as typeof fetch,
        });
        expect(res.ok).toBe(false);
    });
});
