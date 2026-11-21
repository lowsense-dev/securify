# <img src="desktop/src/main/resources/securify.svg" width="44" height="44" alt=""> Securify

**English** | [Русский](README_RU.md)

A proxy client with TUN mode and HWID subscriptions for Android, Linux and Windows. It uses [Xray](https://github.com/XTLS/Xray-core) for the tunnel and [Miuix](https://github.com/compose-miuix-ui/miuix) for the interface.

[Download Securify](../../releases/latest) · [What's new in 1.8.0](.github/release-notes/v1.8.0.md#english)

## Features

- **TUN on all platforms.** Android uses VpnService. Xray creates the network interface on Linux and Windows.
- **Protocols:** VLESS (Reality, Vision, XHTTP), VMess, Trojan, Shadowsocks (including 2022), Hysteria2 (with port hopping), WireGuard.
- **Transports:** RAW/TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP, mKCP. Security is TLS or Reality.
- **Subscriptions** as base64, a plain list of links, or Xray JSON:
  - the name is detected from `profile-title`;
  - the subscription panel shows the description (`announce`), expiry date, remaining traffic (`subscription-userinfo`), and support and subscription page links;
  - HWID headers in the Remnawave format (`x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`), which can be turned off;
  - auto-update on `profile-update-interval`;
  - User-Agent preset: Securify, v2rayNG or Happ, for panels that serve each client differently;
  - per-subscription overrides for sending HWID, the User-Agent and a custom HWID value, set from the subscription menu or while adding it.
- **Auto:** each subscription has an entry that picks the fastest server and switches over when it fails (Xray balancer `leastPing` with the observatory).
- **Import** from the clipboard, a QR code, or by hand. **Export** of subscription and server links as text or a QR code.
- **Ping** in three ways: TCP, ICMP, and real delay through the proxy. All servers are pinged in parallel.
- **Sorting** in subscription order, by name, or by ping. Each subscription has its own page.
- **Routing:** "everything" or "RU direct" (`geosite:category-ru` and `geoip:ru` bypass the proxy).
- **Custom rules:** domains, IPs, `geosite:` and `geoip:` lists sent through the proxy, direct or blocked.
- **Geo files** built in, or downloaded from [Loyalsoldier](https://github.com/Loyalsoldier/v2ray-rules-dat) or [runetfreedom](https://github.com/runetfreedom/russia-v2ray-rules-dat) with a SHA-256 check.
- **Anti-DPI:** TLS ClientHello fragmentation and UDP noise (Xray finalmask).
- **DNS:** your own DoH/DoT server and FakeDNS; apps' port 53 queries are handled by Xray.
- **Per-app split tunneling:** only the selected apps, or all except the selected ones, with user and system app filters.
- **Exit IP and country** on the home screen, along with speed and session time.
- **Quick Settings tile** showing the server name. Long-press opens the app.
- **Launcher shortcuts** to connect and disconnect, and support for the system "Always-on VPN".
- **Xray log** viewer with sharing.
- **In-app updates** from GitHub Releases: the APK for your ABI is checked against `SHA256SUMS` and installed through the system installer. The check runs only when you tap it, since the app's own traffic bypasses the VPN.
- **Monet:** app and icon colors follow the wallpaper. Themed icon on Android 13+.
- **Languages:** English and Russian, picked from the system language.

## Linux

The Linux version is an AppImage with the same features and a Miuix (HyperOS-style) interface: `securify-<version>-x86_64.AppImage` from [Releases](../../releases).

```sh
chmod +x securify-*-x86_64.AppImage && ./securify-*-x86_64.AppImage
```

- Xray creates the TUN interface `securify0` itself, so connecting needs root. The app asks once per session through polkit (`pkexec`). If no polkit agent is running, as on many tiling WMs, it asks for the password itself and uses `sudo`.
- Xray and the geo files are copied to `~/.local/share/securify`, where root can read them. Settings, subscriptions and the HWID live there too.
- Per-app split tunneling matches process names (`firefox`), picked from installed apps or typed by hand.
- The window is a fixed phone-shaped one, 2:3, and can't be resized, maximized or fullscreened; the interface is the mobile one with the bottom tab bar.
- The theme follows the system dark mode and accent color through the XDG portal. Closing it hides the app to the tray: StatusNotifierItem (KDE, Waybar, Quickshell, GNOME with AppIndicator) or a classic X11 tray. The tray menu connects, disconnects and quits.
- "Add to the app menu" in Settings creates a launcher with Connect and Disconnect actions. `--connect` and `--disconnect` also work from a terminal.
- Updates download the new AppImage, check it against `SHA256SUMS`, and replace the running file.

Needs glibc x86_64, FUSE 2 (or `--appimage-extract-and-run`), and polkit or sudo.

## Windows

The Windows build is a portable zip, `securify-<version>-windows-x64.zip`: unpack it and run `Securify.exe`. Nothing else has to be installed, the JRE and Xray are inside.

- Windows asks for administrator rights on every launch: Xray needs them to create the TUN adapter through `wintun.dll`. The app runs elevated as a whole, so it connects without a second prompt.
- Settings and subscriptions live in `%LOCALAPPDATA%\Securify`. Xray runs from the unpacked folder.
- The interface is the same mobile one as on Linux: a fixed 2:3 window with the bottom tab bar, and it can't be fullscreened.
- Closing the window hides the app to the notification area; the tray menu connects, disconnects and quits.
- The exit IP check uses a password-protected HTTP proxy on `127.0.0.1`.
- Enter process names by hand for per-app routing. Traffic counters and in-app updates are not available on Windows yet.

## Building

Requires JDK 21 and the Android SDK (compileSdk 37).

```sh
./gradlew :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/release/`: arm64-v8a, armeabi-v7a, x86_64 and universal.

The Linux AppImage is built with:

```sh
./gradlew :desktop:appImage
```

It lands in `desktop/build/appimage/`. The build downloads Xray-linux-64 and the pinned appimagetool and checks their SHA-256.

The Windows zip is cross-built on Linux:

```sh
./gradlew :desktop:windowsZip
```

It lands in `desktop/build/windows/`. The build downloads the pinned Temurin JRE for Windows, Xray-windows-64 and the Launch4j packaging tool, checks their SHA-256, bundles the app with the Windows Skiko natives and asks Launch4j for an `.exe` with an administrator manifest. ImageMagick (`magick` or `convert`) adds the icon when it is installed.

The screens can be rendered to PNG without launching the app, which is how the design is checked:

```sh
./gradlew :desktop:previewShots
```

The images land in `desktop/build/previews/`.

The first build downloads `libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) v26.10.31 and checks its SHA-256.

### Signing

Release builds are signed with the key from `keystore.properties` in the project root (the file is not committed to git):

```properties
storeFile=release.jks
storePassword=...
keyAlias=securify
keyPassword=...
```

Instead of the file, you can set the environment variables `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Without a key, release builds are signed with the debug key.

## Releases

A tag matching `v*` triggers `.github/workflows/release.yml`. The workflow builds signed APKs, the Linux AppImage and the Windows zip, then publishes them with `SHA256SUMS`:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

A manual run uploads the builds to the workflow without publishing a release.

Required repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `securify` |
| `SIGNING_KEY_PASSWORD` | key password |

`versionName` comes from the tag and `versionCode` is derived from it: `v1.3.0` → `10300`. Tags must look like `v1.2.3`.

## How it works

- `shared/` (`core/`): link and subscription parsing, Xray config building, storage, ping, geo files and updates. Both apps use it.
- `vpn/SecurifyVpn`: brings up the TUN interface and hands its fd to Xray (`CoreController.startLoop`). The app itself is excluded from the VPN, otherwise Xray's outbound traffic would loop back into the tunnel.
- The exit IP is looked up from ip-api.com through a separate Xray HTTP inbound. That inbound listens on a unix socket in the app's private directory, so other apps can't use it.
- `ui/`: Jetpack Compose with Miuix.
- `desktop/`: the Linux and Windows app in Compose Multiplatform with Miuix. On Linux a root helper started through pkexec or sudo runs Xray with its own TUN inbound (`autoSystemRoutingTable`, `autoOutboundsInterface`); on Windows the app is already elevated and starts Xray directly. The exit IP check uses a unix socket on Linux and loopback TCP on Windows.
- `desktop/src/test/.../PreviewShots.kt`: renders the screens to PNG with placeholder data, so the design can be checked without a running Xray.

## Limitations

- Custom rules, DNS and anti-DPI don't apply to Xray JSON subscriptions, which keep their own config. JSON servers are also left out of Auto.
- Shadowsocks plugins (obfs, v2ray-plugin) are not supported.
- Subscriptions are downloaded outside the VPN.
- Linux: without systemd-resolved, DNS queries are caught on port 53 inside the tunnel instead of being set system-wide.
- Linux: switching servers restarts Xray, so connections drop for a moment.
- Linux: Flatpak and Snap apps don't show up in the per-app list; add their process names by hand.
- Linux: while connected, TCP ping becomes ICMP, because Xray would answer the TCP handshake itself.
- Windows: the whole app runs elevated, so Windows shows the UAC prompt at launch.
- Windows: the traffic counters, the installed-apps list and in-app updates are Linux-only for now.

## License

Securify is licensed under [GPL-3.0](LICENSE).

## Third-party licenses

- [Xray-core](https://github.com/XTLS/Xray-core): MPL-2.0.
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite): LGPL-3.0.
- [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded): Apache-2.0.
