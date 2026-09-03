# SonicSound agent notes

## Toolchain
- Node 20+
- JDK **17** only for Android (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk` or Temurin 17). JDK 26 breaks Capacitor `jlink`.
- Android SDK 34; set `sdk.dir` in `android/local.properties` or `ANDROID_HOME`.

## Verify before claiming done
```bash
npm test
npm run lint:loc
npm run lint:features
npm run lint
npm run build
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
cd android && ./gradlew testDebugUnitTest assembleDebug
```

## Surfaces
- Phone: Capacitor WebView + `BackendPlugin` (Capacitor name still `"VLC"`).
- TV: native Leanback (`TvActivity`); React TV shell removed.
- Remote: WS `:30001` requires HMAC auth (protocol 2); login-over-WS only during pairing window.
- Do not commit `*.jks` / secrets. Release keystore lives outside the repo or in CI secrets.

## Feature flags
Keep `src/features.ts` and `android/.../Features.kt` in sync (`npm run lint:features`).

## Smoke
See [docs/smoke-checklist.md](docs/smoke-checklist.md) for live Navidrome e2e coverage.
