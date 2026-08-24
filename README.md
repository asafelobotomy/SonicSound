# SonicSound

Album-centered music client for Subsonic-compatible servers (Navidrome, Airsonic, etc.).

Fork of [SonicLair](https://github.com/thelinkin3000/SonicLair) — rebranded, modernized, and maintained as **SonicSound**.

## Platforms

- **Android phone** (Capacitor)
- **Android TV** (Leanback + Capacitor TV UI, QR/UDP pairing, jukebox)
- **PWA / web** (Vite)
- Desktop shell via Tauri (legacy support)

## Features

- Browse by artist / album; album-centered playback
- Playlists with play-all and shuffle
- Similar-song radio
- Search, random albums on home
- Lyrics via Subsonic `getLyrics` (when the server provides them)
- Android Auto media browser (legacy)
- API version negotiation for older Airsonic / Subsonic servers

## Develop

```bash
npm install
npm start          # Vite dev server
npm test           # Vitest
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

Optional Spotify similarity art (web):

```
VITE_SPOTIFY_CLIENT_ID=
VITE_SPOTIFY_CLIENT_SECRET=
```

## Upstream credit

Original SonicLair by [thelinkin3000](https://github.com/thelinkin3000/SonicLair), MIT License. See [LICENSE](LICENSE).
