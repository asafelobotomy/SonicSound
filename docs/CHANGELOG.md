# 0.2.14

- Fix WMP viz stutter: move AudioManager latency off the frame path; FFT off audio callback
- Harden PCM/spectrum pipeline (non-blocking present, demux outside lock, DVD speed cache)
- Fragment detach guards; leaner Particle/Blazing draws; mode-gated bars/wave sim
- Classic mode polish and JNI/AudioTrack stability from the day audit

# 0.2.13

- Harden WMP visualizers: latency sync, track/Settings lifecycle, classic mode looks (Spikes/Particle/Battery/Ambience)
- Smooth tempo-aware motion; per-track BPM/dynamics with next-track prefetch
- Stereo/surround channel mapping for spectrum and AudioTrack (with stereo fallback downmix)
- In-app fullscreen viz toast; soft spectrum resets across album swaps and EQ-only settings

# 0.2.12

- Fix live WMP visualizers: LibVLC PCM tap via dlopen (Android RTLD_LOCAL), FFT spectrum feed
- Fullscreen control to cycle all visualizer modes; Settings change applies live
- Scale WMP motion with device volume; spectrum AGC for quiet material
- Guard JukeboxFragment detach race on rapid sidebar navigation

# 0.2.5

- TV/web nav: Artists, Albums, Settings, Videos; logo in sidebar; unified nav icons
- Album/Artist sorting; Settings for EQ, ReplayGain, cache, YouTube API
- Now Playing music-video mode: YouTube audio, official-channel matching, follows play queue
- Settings option to allow any YouTube channel when matching songs

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
