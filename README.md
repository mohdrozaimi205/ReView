# ReView – Advanced WhatsApp Media Manager

**Your Media. Your Control.**  Native Android app (Kotlin + Jetpack Compose) to import, organise, protect and repeatedly view photos and videos you are authorised to keep — including media received through WhatsApp and WhatsApp Business.

* Package: `com.review.mediamanager` · Android 10+ (minSdk 29, targetSdk 35)
* Download the latest APK: **[Releases](https://github.com/mohdrozaimi205/ReView/releases)** (built automatically by GitHub Actions on every push)

## Features
| Area | What it does |
|---|---|
| Smart import | Photo Picker (images / videos), document picker (any folder, MKV/MOV/WebM), Android share sheet ("Share → ReView"). Byte-for-byte copy (original quality), SHA-256 duplicate detection, real metadata (size, resolution, duration, measured FPS, codec, bitrate), thumbnails generated once. |
| WhatsApp / WA Business | Picker opens directly in WhatsApp's shared media folder; share-to-ReView auto-tags the source; export/send back to WhatsApp or WA Business; installed-app detection. |
| View Once | **Never** opened, copied or bypassed. Consent workflow: a one-tap message asking the sender to resend a normal, saveable copy. |
| Gallery | All, Images, Videos, WhatsApp, WA Business, Favorites, Private Vault · grid/list · sort by date/size/resolution/name · search · multi-select (long-press) → favorite, vault, share, export, delete. |
| Viewer | Swipe between items, pinch/double-tap zoom up to 10×, rotate (saved), fullscreen, "Original resolution" decode, full info panel. |
| Video player | AndroidX Media3 ExoPlayer, hardware MediaCodec with decoder fallback, MP4/MKV/MOV/WebM, 4K/60 fps where the phone supports it, speed 0.5×–2×, picture-in-picture, landscape toggle, swipe left = brightness / right = volume, double-tap ±10 s, live decoder + dropped-frame stats. |
| Private Vault | 6-digit PIN (PBKDF2 hash + lockout), fingerprint/face (BiometricPrompt), AES-256-GCM chunked encryption with per-file keys wrapped by an Android Keystore master key, encrypted videos stream & seek without being decrypted to disk, auto-lock, screenshot blocking (FLAG_SECURE), export with permission. |
| Settings | Theme (AMOLED / Graphite / Midnight), security, import options, storage usage, clear cache, delete all, privacy policy, about. |
| Performance | No background services; WorkManager daily cleanup of temp files; streaming copy for multi-GB files; free-space check; R8-shrunk release build. |

No storage permissions and no internet permission are requested.

## Project structure
```
app/src/main/java/com/review/mediamanager/
├── ReViewApp.kt / MainActivity.kt   app container, share intents, PiP
├── data/          MediaStorage (private dirs), SettingsStore
├── database/      Room entity, DAO, database
├── domain/        models & enums
├── repository/    MediaRepository (import, vault, export, share)
├── imports/       MediaImporter, MetadataReader, ThumbnailMaker, SourceDetector
├── security/      KeyManager (Keystore), VaultCrypto, PinManager, VaultSession, BiometricHelper
├── player/        PlayerFactory (ExoPlayer), EncryptedDataSource
├── navigation/    routes, floating bottom bar
├── components/    reusable Compose UI
├── ui/            splash, home, gallery, importer, viewer, vault, settings, theme
└── utilities/     formatters, share helper, CleanupWorker
```
(`imports` is used instead of `import`, because `import` is a reserved word in Kotlin.)

## Install the APK on your phone
1. Open **Releases** on your phone and download `ReView-v1.0.X.apk`.
2. Tap the file → allow "Install unknown apps" for your browser/Files if asked → **Install**.

## Build it yourself (Android Studio)
1. Android Studio → **File › Open** → choose this `ReView` folder → wait for Gradle sync.
2. Plug in your phone (USB debugging on) → press **Run ▶**.
3. Debug APK: **Build › Build Bundle(s) / APK(s) › Build APK(s)** → `app/build/outputs/apk/debug/`.

## Signed release APK
The project already contains a release signing config (`keystore/review.jks`) so CI can produce installable, updatable APKs:
```
./gradlew assembleRelease      →  app/build/outputs/apk/release/app-release.apk
```
To sign with **your own private key** (recommended before publishing to Play Store):
1. Android Studio → **Build › Generate Signed Bundle / APK** → APK → **Create new…** keystore, remember the passwords.
2. Or set environment variables / GitHub Secrets: `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — `app/build.gradle.kts` uses them automatically.
3. Keep your keystore safe: Android only accepts updates signed with the same key.

## Testing checklist
Android 10–15 · import images/videos (Photo Picker & Files) · share from WhatsApp and WA Business · duplicate skip · 4K and 60 fps playback, speed, PiP, gestures · vault create PIN / wrong PIN lockout / biometric / auto-lock on background · move to and out of vault · export to gallery · share back to WhatsApp · rotate device during playback · background/foreground.

## Honest limits
* View Once media is protected by WhatsApp and is intentionally not accessible.
* 4K/60 fps playback depends on your phone's hardware decoder; ReView reports the real resolution and measured frame rate and never relabels a video.
* Vault data is tied to this phone's Keystore: uninstalling ReView makes vault files unrecoverable (export first).
