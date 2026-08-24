import { describe, expect, it } from "vitest";
import {
    API_VERSION_CANDIDATES,
    buildAuthParams,
    isVersionRejectedMessage,
    randomSalt,
} from "../subsonic/auth";
import { resolveCoverArtId } from "../player/queue";

describe("subsonic auth", () => {
    it("generates unique salts", () => {
        const a = randomSalt();
        const b = randomSalt();
        expect(a).toHaveLength(16);
        expect(a).not.toEqual(b);
    });

    it("builds token auth params with client sonicsound", () => {
        const params = buildAuthParams(
            { username: "u", password: "p", usePlaintext: false },
            "1.15.0"
        );
        expect(params.c).toBe("sonicsound");
        expect(params.v).toBe("1.15.0");
        expect(params.t).toBeTruthy();
        expect(params.s).toBeTruthy();
        expect(params.p).toBeUndefined();
    });

    it("builds plaintext params when requested", () => {
        const params = buildAuthParams(
            { username: "u", password: "secret", usePlaintext: true },
            "1.13.0"
        );
        expect(params.p).toBe("secret");
        expect(params.t).toBeUndefined();
    });

    it("detects incompatible protocol messages", () => {
        expect(
            isVersionRejectedMessage(
                "Incompatible Airsonic REST protocol version. Server must upgrade"
            )
        ).toBe(true);
        expect(isVersionRejectedMessage("Wrong username or password")).toBe(
            false
        );
    });

    it("tries newer API versions first", () => {
        expect(API_VERSION_CANDIDATES[0]).toBe("1.16.1");
        expect(API_VERSION_CANDIDATES.length).toBeGreaterThan(1);
    });
});

describe("cover art id", () => {
    it("prefers coverArt over albumId", () => {
        expect(
            resolveCoverArtId({ coverArt: "c1", albumId: "a1" })
        ).toBe("c1");
        expect(resolveCoverArtId({ albumId: "a1" })).toBe("a1");
    });
});
