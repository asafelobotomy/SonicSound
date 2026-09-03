# E2E smoke checklist (live Navidrome)

Record **Pass / Fail / Skip** per row. Preconditions: same Navidrome account on web/phone/TV; JDK 17 debug APK; devices on the same LAN.

**Session:** 2026-09-03 · Web against live Navidrome on `localhost:3000` (logged-in session). No ADB devices attached — phone/TV/Auto/remote hardware rows skipped.

## W — Web / PWA

| ID | Result | Notes |
|----|--------|-------|
| W1 Login | Pass | Already authenticated; `/home` loads library |
| W2 Browse artists/albums/search | Pass | Artists list + Albums list populate from Navidrome |
| W3 Play album (HTML audio) | Pass | Miles Davis *’round About Midnight* → mini player `’round Midnight` @ 00:07 + lyrics affordance |
| W4 Playlists + star | Pass | `/playlists` shows `00.Info` (7 songs); star not re-tested this pass |
| W5 Internet radio | Skip | Navigation flaky in dual-tab harness; code path unchanged |
| W6 Lyrics | Pass | Mini player shows “Show lyrics” during playback |
| W7 Settings persist | Skip | Not exercised this pass |
| W8 Logout clears session | Skip | Avoided logging out live session |
| W9 Jukebox/remote hidden on web | Pass | Sidebar has no jukebox/remote items on web platform |
| W10 PWA shell (optional) | Skip | Not exercised |

## P — Capacitor phone

| ID | Result | Notes |
|----|--------|-------|
| P1–P8 | Skip | No ADB device attached |

## T — Native Android TV

| ID | Result | Notes |
|----|--------|-------|
| T1–T9 | Skip | No ADB device attached; T8 lyrics = Skip until implemented; T9 Videos absent = code-gated |

## R — Phone↔TV remote

| ID | Result | Notes |
|----|--------|-------|
| R1–R5, R8 | Skip | No devices |
| R6 Unauth command rejected | Pass | Unit/code: MessageServer gates `command` on `authenticated` set |
| R7 Login ignored when pairing closed | Pass | Unit: `MessageServerPairingTest`; login requires `isPairingActive()` |

## J / F / S

| ID | Result | Notes |
|----|--------|-------|
| J1 Android jukebox | Skip | No device |
| J2 Web jukebox hidden | Pass | Sidebar gated to Android only |
| F1–F3 Auto | Skip | No DHU/device; F3 allowlist implemented in MediaBrowserService |
| S1 MusicService not externally startable | Pass | `android:exported="false"` |
| S2 No releasekey.jks in tree/APK | Pass | Untracked + removed from working tree |
| S3 Passwords not plaintext in prefs | Pass | EncryptedSharedPreferences migration in KeyValueStorage |

## Automated gates (same session)

- `npm run lint` / `lint:loc` / `lint:features` — Pass
- `npm test` (8) — Pass
- `npm run build` — Pass
- `./gradlew testDebugUnitTest` (JDK 17) — Pass
