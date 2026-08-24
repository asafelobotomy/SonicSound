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
