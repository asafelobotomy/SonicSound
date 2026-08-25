# 0.2.4

- Shield QoL: null-safe playlists (no empty-list crash), fragment restore hardening
- Login: password visibility (TV + web), LAN IP fix for QR/UDP, Subsonic/Navidrome LAN discover
- Internet Radio via getInternetRadioStations (TV + web)
- TV Now Playing backdrop polish; optional LibVLC EQ/ReplayGain settings
- Docs: music videos deferred to official YouTube API + IFrame only (no scrapers)

# 0.2.3

- Fix Shield crash: compile Globals/IBroadcastObserver as Kotlin (Java under kotlin/ was omitted from the APK)
- Harden LAN discovery null handling on Android TV

# 0.2.2

- Ship arm64-v8a-only APKs for Shield Pro / modern devices (drops unused 32-bit VLC libs)

# 0.2.1

- Shrink APK ~50% for Shield/TV installs: drop x86 emulator libs bundled by libvlc-all

# 0.2.0

- Local JDK 17 + Android SDK 34 debug APK path
- Android Auto: browsable playlists, warmer caches, voice search routing
- Google Assistant: handle MEDIA_PLAY_FROM_SEARCH on cold start
- PWA: vite-plugin-pwa service worker + Media Session streaming exclusions
- Native credentials via Capacitor Preferences; web still localStorage-only
- F-Droid metadata scaffold + reproducible build docs

# 0.1.0

- Rebrand SonicLair → SonicSound (app.sonicsound)
- Vite + Capacitor 6 + Android SDK 34
- Subsonic API version negotiation
- 400 LOC file limit + modular Subsonic/player architecture
- Cover art, seek, lyrics, random albums, play/shuffle
- Security: remove hardcoded keystore/Spotify secrets
