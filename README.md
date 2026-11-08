# Securify

**English** | [Русский](README_RU.md)

An Android proxy client with TUN mode, built on the [Xray](https://github.com/XTLS/Xray-core) core. It supports subscriptions with HWID and uses a Material 3 Expressive design.

## Features

- **TUN via VpnService.** All device traffic goes through Xray: TCP, UDP, IPv4 and IPv6.
- **Protocols:** VLESS (Reality, Vision, XHTTP), VMess, Trojan, Shadowsocks (including 2022), Hysteria2.
- **Transports:** RAW/TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP, mKCP. Security is TLS or Reality.
- **Subscriptions** as base64, a plain list of links, or Xray JSON:
  - the name is detected from `profile-title`;
  - the subscription panel shows the description (`announce`), expiry date, remaining traffic (`subscription-userinfo`), and support and subscription page links;
  - HWID headers in the Remnawave format (`x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`), which can be turned off;
  - auto-update on `profile-update-interval`.
- **Import** from the clipboard, a QR code, or by hand.
- **Ping** in three ways: TCP, ICMP, and real delay through the proxy. All servers are pinged in parallel.
- **Sorting** in subscription order, by name, or by ping. Each subscription has its own page.
- **Routing:** "everything" or "RU direct" (`geosite:category-ru` and `geoip:ru` bypass the proxy).
- **Per-app split tunneling:** only the selected apps, or all except the selected ones.
- **Exit IP and country** on the home screen, along with speed and session time.
- **Quick Settings tile** showing the server name. Long-press opens the app.
- **Monet:** app and icon colors follow the wallpaper. Themed icon on Android 13+.
- **Languages:** English and Russian, picked from the system language.

## Building

Requires JDK 21 and the Android SDK (compileSdk 37).

```sh
./gradlew :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/release/`: arm64-v8a, armeabi-v7a, x86_64 and universal.

The first build downloads `libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) v26.10.31 and checks its SHA-256.

Tests:

```sh
./gradlew :app:testDebugUnitTest
```

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

A tag matching `v*` triggers `.github/workflows/release.yml`. The workflow runs the tests, builds signed APKs and publishes them to a GitHub Release along with `SHA256SUMS`:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

A manual run (workflow_dispatch) uploads the APKs as a build artifact instead.

Required repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `securify` |
| `SIGNING_KEY_PASSWORD` | key password |

`versionName` comes from the tag and `versionCode` is derived from it: `v1.3.0` → `10300`. Tags must look like `v1.2.3`.

## How it works

- `core/`: link and subscription parsing, Xray config building, storage, ping.
- `vpn/SecurifyVpn`: brings up the TUN interface and hands its fd to Xray (`CoreController.startLoop`). The app itself is excluded from the VPN, otherwise Xray's outbound traffic would loop back into the tunnel.
- The exit IP is looked up from ip-api.com through a separate Xray HTTP inbound. That inbound listens on a unix socket in the app's private directory, so other apps can't use it.
- `ui/`: Jetpack Compose with Material 3 Expressive.

## Limitations

- Hysteria2 uses the first port of a range (port hopping is not supported).
- Shadowsocks plugins (obfs, v2ray-plugin) are not supported.
- Subscriptions are downloaded outside the VPN.
- The ip-api.com request goes over plain HTTP, without encryption.

## License

Securify is licensed under [GPL-3.0](LICENSE).

## Third-party licenses

- [Xray-core](https://github.com/XTLS/Xray-core): MPL-2.0.
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite): LGPL-3.0.
- [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded): Apache-2.0.
