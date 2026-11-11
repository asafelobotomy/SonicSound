import { afterEach, describe, expect, it } from "vitest";
import {
    startUqTokens,
    tauriTokensBridge,
    TOKENS_CHANGED_EVENT,
} from "../uq/uqTokens";
import {
    PROPERTIES,
    VERSION,
    clearTokens,
} from "../vendor/uq-tokens/uq-tokens.js";

const root = document.documentElement;

/** A fake desktop shell: `window.__TAURI__` serving `text` and firing `uq-tokens-changed`. */
function fakeShell(initial: string | null) {
    const shell = {
        text: initial,
        fire: () => {},
        unlistened: false,
        host: {} as Parameters<typeof startUqTokens>[0],
    };
    shell.host = {
        __TAURI__: {
            invoke: async (cmd: string) =>
                cmd === "uq_tokens" ? shell.text : null,
            event: {
                listen: async (event: string, handler: () => void) => {
                    if (event === TOKENS_CHANGED_EVENT) shell.fire = handler;
                    return () => {
                        shell.unlistened = true;
                    };
                },
            },
        },
    };
    return shell;
}

const tokens = (colors: Record<string, string>) =>
    JSON.stringify({
        variant: "dark",
        colors,
        ui: { radius: 6 },
        fonts: { ui: "Inter", mono: "JetBrains Mono" },
    });

afterEach(() => clearTokens(root));

describe("vendored uq-tokens", () => {
    it("is the pinned version", () => {
        expect(VERSION).toBe("1.0.0");
        expect(PROPERTIES).toContain("--uq-bg");
    });
});

describe("uQ theme in the desktop shell", () => {
    it("applies uQ's tokens to <html> and follows changes", async () => {
        const shell = fakeShell(tokens({ bg: "#101010", text: "#fafafa" }));
        const watch = startUqTokens(shell.host);
        expect(watch).not.toBeNull();
        await watch!.ready;
        expect(root.style.getPropertyValue("--uq-bg")).toBe("#101010");
        expect(root.style.getPropertyValue("--uq-text")).toBe("#fafafa");
        expect(root.style.getPropertyValue("--uq-radius")).toBe("6px");
        expect(root.style.getPropertyValue("--uq-font-ui")).toBe('"Inter"');
        expect(root.getAttribute("data-uq-variant")).toBe("dark");

        shell.text = tokens({ bg: "#202020" });
        shell.fire();
        await watch!.refresh();
        expect(root.style.getPropertyValue("--uq-bg")).toBe("#202020");
        expect(root.style.getPropertyValue("--uq-text")).toBe("");

        await new Promise((r) => setTimeout(r, 0));
        watch!.stop();
        expect(shell.unlistened).toBe(true);
    });

    it("falls back to SonicSound's palette when the file is missing or broken", async () => {
        const shell = fakeShell(tokens({ bg: "#101010" }));
        const watch = startUqTokens(shell.host)!;
        await watch.ready;
        expect(root.hasAttribute("data-uq-variant")).toBe(true);

        shell.text = null; // uQ not installed, or the file went away
        await watch.refresh();
        expect(root.style.getPropertyValue("--uq-bg")).toBe("");
        expect(root.hasAttribute("data-uq-variant")).toBe(false);

        shell.text = "{not json";
        await watch.refresh();
        expect(root.style.length).toBe(0);
        watch.stop();
    });

    it("rejects an injection attempt and keeps the valid values", async () => {
        const shell = fakeShell(
            JSON.stringify({
                variant: "dark;}body{display:none",
                colors: { bg: "red;}body{display:none", text: "#fafafa" },
                fonts: {
                    ui: 'x";}body{background:url(//evil)}',
                    mono: "url(//evil)",
                },
                ui: { radius: "1px;}" },
            })
        );
        const watch = startUqTokens(shell.host)!;
        await watch.ready;
        expect(root.style.getPropertyValue("--uq-bg")).toBe("");
        expect(root.style.getPropertyValue("--uq-font-ui")).toBe("");
        expect(root.style.getPropertyValue("--uq-font-mono")).toBe("");
        expect(root.style.getPropertyValue("--uq-radius")).toBe("");
        expect(root.hasAttribute("data-uq-variant")).toBe(false);
        expect(root.style.getPropertyValue("--uq-text")).toBe("#fafafa");
        watch.stop();
    });
});

describe("outside the desktop shell", () => {
    it("does nothing in a browser or the Capacitor app", () => {
        expect(startUqTokens({})).toBeNull();
        const capacitor = {
            ...fakeShell("{}").host,
            Capacitor: { isNativePlatform: () => true },
        };
        expect(tauriTokensBridge(capacitor)).toBeNull();
        expect(root.hasAttribute("data-uq-variant")).toBe(false);
        expect(root.style.length).toBe(0);
    });

    it("finds invoke in Tauri 2's `core` too", () => {
        const host = {
            __TAURI__: {
                core: { invoke: async () => "{}" },
                event: { listen: async () => () => {} },
            },
        };
        expect(tauriTokensBridge(host)).not.toBeNull();
    });
});
