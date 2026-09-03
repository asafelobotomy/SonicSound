# SonicSound

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/U5R225QZH3)

Album-centered music client for Subsonic-compatible servers (Navidrome, Airsonic, etc.).

Fork of [SonicLair](https://github.com/thelinkin3000/SonicLair) — rebranded, modernized, and maintained as **SonicSound**.

## Platforms

- **Android phone** (Capacitor)
- **Android TV** (Leanback, Remote phone control, Jukebox Collections, UDP/mDNS discovery)
- **PWA / web** (Vite)
- Desktop shell (`src-tauri/`) deferred — not in the active npm toolchain

## Features

- Browse by artist / album; album-centered playback
- Playlists with play-all and shuffle
- Similar-song radio
- Search, random albums on home
- Lyrics via Subsonic `getLyrics` (when the server provides them)
- Android Auto media browser (legacy)
- API version negotiation for older Airsonic / Subsonic servers

## Layout

| Path | Purpose |
|------|---------|
| `src/` | React UI, Subsonic client, Capacitor plugins |
| `android/` | Native Android + TV |
| `brand/` | Logo masters (derive public/ and platform icons from here) |
| `public/` | Favicon, PWA icons, manifest |
| `docs/` | Changelog, F-Droid notes, smoke checklist, music-videos (deferred), store screenshots |
| `scripts/` | LOC gate and helper scripts |

## Develop

```bash
npm install
npm start          # Vite dev server
npm test           # Vitest
npm run lint       # ESLint (max-lines gate)
npm run lint:loc   # Fail if any .ts/.tsx/.kt/.java file exceeds 400 LOC
npm run build      # Production web bundle → build/
```

### Android

Requires **JDK 17** (`JAVA_HOME` pointing at JDK 17 — newer JDKs like 26 break Capacitor’s `jlink` transform) and **Node 20+**.

1. Install Android SDK (API 34); copy `android/local.properties.example` → `android/local.properties` and set `sdk.dir` (or export `ANDROID_HOME` / `ANDROID_SDK_ROOT`).
2. Optional release signing (see example file — **never commit keystores or secrets**).
3. Build web + sync:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # adjust to your JDK 17 path
npm run cap:sync
cd android && ./gradlew assembleDebug
```

Application id: `app.sonicsound`

#### Release signing

Release keystores must live **outside the repo** (for example `~/.local/share/sonicsound/release.jks`) or in CI secrets (`SONICSOUND_KEYSTORE*`). Point `sonicsound.keystore` in `local.properties` at that path. Do not commit `*.jks` / `*.keystore`. If a key was ever committed historically, rotate it and update GitHub Actions secrets; history purge is a separate ops step.

### Environment

Optional Spotify similarity art (**dev web only** — not shipped in production web or native APK builds). Copy [`.env.example`](.env.example):

```
VITE_SPOTIFY_CLIENT_ID=
VITE_SPOTIFY_CLIENT_SECRET=
```

Native Android no longer embeds Spotify client secrets in BuildConfig. Spotify artist-art fallback needs a future server-side token proxy.

Prefer an `https://` Subsonic/Navidrome URL when your server supports TLS. Android still allows cleartext HTTP for LAN servers (Network Security Config cannot express private CIDRs); HTTPS is strongly preferred.

F-Droid packaging notes: [docs/fdroid.md](docs/fdroid.md). Multi-ABI: `./gradlew assembleRelease -Pabis=armeabi-v7a,arm64-v8a`.

Live Navidrome e2e: [docs/smoke-checklist.md](docs/smoke-checklist.md). Music videos: [docs/music-videos.md](docs/music-videos.md) (deferred / gated off).

## Upstream credit

Original SonicLair by [thelinkin3000](https://github.com/thelinkin3000/SonicLair), MIT License. See [LICENSE](LICENSE).
