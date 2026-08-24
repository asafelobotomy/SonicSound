# F-Droid reproducible build notes

SonicSound targets F-Droid with an unsigned release APK produced without
proprietary signing secrets.

## Recipe (aligned with `metadata/app.sonicsound.yml`)

Requirements:

- JDK **17**
- Node.js 20+
- Android SDK platform **34** + build-tools **34.0.0**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # or your JDK 17 path
npm ci
npm run build
npx cap sync android
cd android
./gradlew assembleRelease
```

Output (debug-friendly unsigned release when no keystore is configured):

`android/app/build/outputs/apk/release/app-release-unsigned.apk`

Do **not** set `SONICSOUND_KEYSTORE*` / `sonicsound.keystore*` for F-Droid
builds. GitHub Actions release signing is for tagged Play-style artifacts only.

## AntiFeatures

- **NonFreeNet** — optional Spotify artist-art similarity uses Spotify’s API
  when `spotify.clientId` / `SPOTIFY_*` are configured. Core Subsonic playback
  does not require this.

## Metadata

See [`metadata/app.sonicsound.yml`](../metadata/app.sonicsound.yml) for the
fdroiddata-style recipe scaffold. Submitting to f-droid.org is a separate
manual ops step after a dry-run succeeds.
