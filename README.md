# SonicSound

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/U5R225QZH3)

Album-centered music client for Subsonic-compatible servers (Navidrome, Airsonic, etc.).

Fork of [SonicLair](https://github.com/thelinkin3000/SonicLair) — rebranded, modernized, and maintained as **SonicSound**.

## Platforms

- **Android phone** (Capacitor)
- **Android TV** (Leanback + Capacitor TV UI, QR/UDP pairing, jukebox)
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
| `docs/` | Changelog, store screenshots |
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

1. Install Android SDK; copy `android/local.properties.example` → `android/local.properties` and set `sdk.dir`.
2. Optional release signing / Spotify (see example file — **never commit secrets**).
3. Build web + sync:

```bash
npm run cap:sync
cd android && ./gradlew assembleDebug
```

Application id: `app.sonicsound`

### Environment

Optional Spotify similarity art (**dev web only** — not shipped in production web builds):

```
VITE_SPOTIFY_CLIENT_ID=
VITE_SPOTIFY_CLIENT_SECRET=
```

Prefer an `https://` Subsonic/Navidrome URL when your server supports TLS. Android allows cleartext HTTP for LAN servers that do not offer HTTPS.

F-Droid packaging notes: [docs/fdroid.md](docs/fdroid.md).

## Upstream credit

Original SonicLair by [thelinkin3000](https://github.com/thelinkin3000/SonicLair), MIT License. See [LICENSE](LICENSE).
