import type { QuickConnectAccount, QuickConnectSealed } from "./qcCrypto";

/**
 * Browser-page side of Quick Connect. Talks same-origin HTTP to the TV page server
 * (QuickConnectHttpServer.kt) so no cross-port websocket is needed.
 */
export interface QuickConnectOptions {
    baseUrl: string;
    code: string;
    account: QuickConnectAccount;
    seal: (tvPub: string, code: string, plaintext: string) => QuickConnectSealed;
    onProgress?: (text: string) => void;
    fetchImpl?: typeof fetch;
}

export interface QuickConnectResult {
    ok: boolean;
    message: string;
}

interface Reply {
    status: string;
    data: string;
}

export async function runQuickConnect(opts: QuickConnectOptions): Promise<QuickConnectResult> {
    const doFetch = opts.fetchImpl ?? fetch;
    try {
        const helloRes = await doFetch(`${opts.baseUrl}/api/qc/hello`, { cache: "no-store" });
        const hello = (await helloRes.json()) as Reply;
        if (hello.status !== "ok") return { ok: false, message: hello.data };
        const { tvPub } = JSON.parse(hello.data) as { tvPub: string };
        const sealed = opts.seal(tvPub, opts.code, JSON.stringify(opts.account));
        opts.onProgress?.("Sent. Waiting for the TV to sign in…");
        const loginRes = await doFetch(`${opts.baseUrl}/api/qc/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(sealed),
        });
        const reply = (await loginRes.json()) as Reply;
        return { ok: reply.status === "ok", message: reply.data };
    } catch {
        return { ok: false, message: "Couldn't reach the TV. Is Quick Connect still open?" };
    }
}
