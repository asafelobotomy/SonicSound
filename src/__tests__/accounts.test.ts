import { afterEach, describe, expect, it } from "vitest";
import {
    clearStoredContext,
    loadStoredContext,
    persistContext,
} from "../player/accounts";
import { IAppContext } from "../Models/AppContext";

describe("accounts storage", () => {
    afterEach(() => {
        clearStoredContext();
    });

    it("returns empty context when nothing stored", () => {
        clearStoredContext();
        const ctx = loadStoredContext();
        expect(ctx.activeAccount.username).toBeNull();
        expect(ctx.accounts).toEqual([]);
    });

    it("round-trips persisted context", () => {
        const next: IAppContext = {
            activeAccount: {
                username: "u",
                password: "p",
                url: "http://example.local",
                type: "navidrome",
                usePlaintext: false,
            },
            accounts: [],
            spotifyToken: "",
        };
        persistContext(next);
        const loaded = loadStoredContext();
        expect(loaded.activeAccount.username).toBe("u");
        expect(loaded.activeAccount.url).toBe("http://example.local");
    });
});
