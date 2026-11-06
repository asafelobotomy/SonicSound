import { describe, expect, it } from "vitest";
import { _testing, open, seal } from "./qcCrypto";

const { p256, toB64 } = _testing;
const hex = (h: string) => Uint8Array.from(h.match(/../g)!.map((x) => parseInt(x, 16)));

// Same vector is asserted in android QuickConnectCryptoTest.kt, so both sides must agree.
const TV_SECRET = hex("1".repeat(64));
const VECTOR = {
    tvPub: "BAIX5hfwtkQ5KCePlpmeaaI6TywVK99tbN9m5bgCgtTtGUp968uXcS0t2jyoWqh2Wlb0X8dYWZZS8ol8ZTBuV5Q=",
    clientPub: "BNZak5d8qj0bCBhS/1ennkZfFmBXcwS66tUF3TpIWJzzUBheiVNy32Ih6joTdVfkc/3bZ1XwW9UHw8Uz/OnJEoU=",
    nonce: "MzMzMzMzMzMzMzMz",
    ct: "m+Pg3DYPZ6lny3us8GUUnEyfcKpW2BH2Qw==",
};

describe("quick connect crypto", () => {
    it("produces the shared cross-platform vector", () => {
        expect(toB64(p256.getPublicKey(TV_SECRET, false))).toBe(VECTOR.tvPub);
        const sealed = seal(VECTOR.tvPub, "482913", '{"u":"x"}', {
            clientSecret: hex("2".repeat(64)),
            nonce: hex("3".repeat(24)),
        });
        expect(sealed).toEqual({ clientPub: VECTOR.clientPub, nonce: VECTOR.nonce, ct: VECTOR.ct });
    });

    it("round-trips with random keys and rejects a wrong code", () => {
        const sealed = seal(VECTOR.tvPub, "123456", "secret");
        expect(open(TV_SECRET, "123456", sealed)).toBe("secret");
        expect(open(TV_SECRET, "123457", sealed)).toBeNull();
    });
});
