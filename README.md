# LinkShare

LinkShare is an offline-first file sharing app for local networks. It provides an Android app and Compose Desktop targets for Windows, macOS, and Debian-based Linux systems. Files are transferred directly between devices; there is no cloud relay or account service.

## Current release

The current development release is `1.2.0` (`versionCode 2`). Release artifacts are produced by GitHub Actions for Android, Windows, macOS, and Linux when a `v*` tag is pushed.

The repository also contains iOS source sets, but this repository does not currently publish a signed iOS application.

## What works today

- HTTP sharing server bound to all local interfaces.
- PIN-protected web file browser with upload, download, delete, range streaming, and media playback.
- WebDAV methods used by common clients: `PROPFIND`, `MKCOL`, and `PUT`.
- Separate FTP server with PIN authentication.
- Directory selection with Android internal storage and detected mounted volumes.
- LAN discovery by scanning active local IPv4 subnet prefixes.
- QR-based connection flow and Android QR scanner.
- Android local-only hotspot hosting when Android and device permissions allow it.
- Foreground sharing service and Wi-Fi lock while sharing in the background.
- Audio and video web players with minimize controls and keyboard shortcuts.
- Android installed-app listing, APK extraction into the shared directory, and Android installer handoff.
- SHA-256 verified piece manifests, authenticated piece endpoints, resumable piece storage, and swarm scheduling foundations.

## Important limitations

- Android does not allow an app to silently join arbitrary Wi-Fi networks on modern versions. The receiver may need to approve or complete the connection in system UI.
- Dual-link detection and scheduling are implemented, but LinkShare does not yet bond two interfaces into one kernel-level connection. The current transport uses separate HTTP requests and requires real multi-interface testing before claiming a speed improvement.
- Swarm transfer currently exposes verified piece transport and a JVM coordinator. It is not a complete BitTorrent-compatible protocol and does not interoperate with `.torrent` clients.
- Android APK installation still requires Android’s package-installer approval and may require enabling installation from this source.
- Windows firewall rules are outside the app. If inbound traffic is blocked, allow the selected server ports in Windows Firewall.
- The app has not been certified against every router, hotspot implementation, SD card provider, FTP client, or WebDAV client.

## Supported release artifacts

| Platform | Artifact | Notes |
| --- | --- | --- |
| Android | APK | API 26+; release APK is signed with the repository’s stable release key configured in GitHub Actions |
| Windows | MSI | Built with Compose Desktop and WiX on GitHub Actions |
| macOS | DMG | Built on a macOS GitHub Actions runner |
| Linux | DEB | Built on an Ubuntu GitHub Actions runner |

Do not install a debug APK over a release APK, or vice versa. Android requires the same signing certificate for an in-place update. Very old development builds may need to be uninstalled once before installing the stable-signed release; future releases use the same stable signing key and increasing version codes.

## Build locally

Requirements:

- JDK 17 or newer
- Android SDK 34 for Android builds
- Gradle wrapper included in this repository

```bash
git clone https://github.com/Flaxmbot/LinkShare.git
cd LinkShare

# Compile desktop and Android code
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid

# Android debug APK
./gradlew :composeApp:assembleDebug

# Android release APK; requires keystore/release.jks or release signing environment variables
./gradlew :composeApp:assembleRelease

# Desktop packages; run the target that matches the host OS
./gradlew :composeApp:packageMsi
./gradlew :composeApp:packageDmg
./gradlew :composeApp:packageDeb
```

The Android debug APK is written to `composeApp/build/outputs/apk/debug/`. Desktop packages are written below `composeApp/build/compose/binaries/`.

## Server endpoints

All endpoints except `/api/status` require the session PIN, either as `pin` query parameter, Basic authentication password, or the server bearer token where applicable.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/status` | Safe server status and device name |
| `GET` | `/api/browse?path=/` | Browse a shared directory |
| `GET` | `/api/stream?path=/file` | Stream a file with range support |
| `GET` | `/api/download?path=/file` | Download a file |
| `POST` | `/api/upload?path=/directory` | Multipart upload |
| `POST` | `/api/delete?path=/file` | Delete a file or directory |
| `GET` / `POST` | `/api/clipboard` | Read or update shared clipboard text |
| `GET` | `/api/swarm/manifest?path=/file` | Get the SHA-256 piece manifest |
| `GET` | `/api/swarm/piece?path=/file&piece=0` | Read one verified-transfer piece |
| `PROPFIND` | `/path` | WebDAV directory listing |
| `MKCOL` | `/path` | WebDAV directory creation |
| `PUT` | `/path/file` | WebDAV file upload |

## Security model

LinkShare is intended for trusted local networks. The HTTP, FTP, WebDAV, and swarm endpoints use the current sharing session’s PIN. Traffic is local and cleartext by default; LinkShare does not currently provide TLS or remote-internet hardening. Stop sharing when it is no longer needed and avoid exposing the server port outside the local network.

## Release process

1. Update `versionName`, `versionCode`, and `packageVersion` together.
2. Run Android and desktop compilation plus `git diff --check`.
3. Build the Android debug or release artifact locally when possible.
4. Commit and push the changes.
5. Push a tag such as `v1.2.0`.
6. GitHub Actions builds Android, Windows, macOS, and Linux artifacts and creates the release.

The Android workflow requires these GitHub Actions secrets: `LINKSHARE_KEYSTORE_BASE64`, `LINKSHARE_KEYSTORE_PASSWORD`, `LINKSHARE_KEY_ALIAS`, and `LINKSHARE_KEY_PASSWORD`.

## License

LinkShare is licensed under the [Apache License 2.0](LICENSE).
