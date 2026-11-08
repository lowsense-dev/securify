# Securify

[English](README.md) | **Русский**

Android-клиент для прокси с TUN-режимом на ядре [Xray](https://github.com/XTLS/Xray-core), с поддержкой подписок и HWID, в дизайне Material 3 Expressive.

## Возможности

- **TUN через VpnService.** Весь трафик устройства идёт через Xray: TCP, UDP, IPv4 и IPv6.
- **Протоколы:** VLESS (Reality, Vision, XHTTP), VMess, Trojan, Shadowsocks (в том числе 2022), Hysteria2.
- **Транспорты:** RAW/TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP, mKCP. Шифрование TLS или Reality.
- **Подписки** в base64, обычным списком ссылок или в Xray JSON:
  - название берётся само из `profile-title`;
  - панель подписки показывает описание (`announce`), дату окончания и остаток трафика (`subscription-userinfo`), ссылки на поддержку и страницу подписки;
  - HWID-заголовки в формате Remnawave (`x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`), отправку можно выключить;
  - автообновление по `profile-update-interval`.
- **Импорт** из буфера обмена, по QR-коду или вручную.
- **Пинг** тремя способами: TCP, ICMP и реальная задержка через прокси. Все сервера пингуются параллельно.
- **Сортировка** как в подписке, по названию или по пингу. У каждой подписки своя страница.
- **Маршрутизация:** «весь трафик» или «RU напрямую» (`geosite:category-ru` и `geoip:ru` идут мимо прокси).
- **Раздельное туннелирование по приложениям:** только выбранные или все, кроме выбранных.
- **Внешний IP и страна** выхода на главном экране, вместе со скоростью и временем сессии.
- **Плитка в быстрых настройках** с названием сервера. Долгое нажатие открывает приложение.
- **Monet:** цвета приложения и иконки берутся из обоев, есть themed-иконка (Android 13+).
- **Язык:** русский и английский, выбирается по языку системы.

## Сборка

Нужны JDK 21 и Android SDK (compileSdk 37).

```sh
./gradlew :app:assembleRelease
```

APK появятся в `app/build/outputs/apk/release/`: под arm64-v8a, armeabi-v7a, x86_64 и universal.

При первой сборке скачивается `libv2ray.aar` из [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) v26.10.31, сборка проверяет его SHA-256.

Тесты:

```sh
./gradlew :app:testDebugUnitTest
```

### Подпись

Release подписывается ключом из `keystore.properties` в корне проекта (файл в git не попадает):

```properties
storeFile=release.jks
storePassword=...
keyAlias=securify
keyPassword=...
```

Вместо файла можно задать переменные окружения `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` и `SIGNING_KEY_PASSWORD`. Если ключа нет, release подписывается debug-ключом.

## Релизы

Тег вида `v*` запускает `.github/workflows/release.yml`. Workflow прогоняет тесты, собирает подписанные APK и публикует их в GitHub Release вместе с `SHA256SUMS`:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

При ручном запуске (workflow_dispatch) APK сохраняются как артефакт сборки.

Нужные секреты репозитория:

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | пароль хранилища |
| `SIGNING_KEY_ALIAS` | `securify` |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

`versionName` берётся из тега, `versionCode` считается из него же: `v1.3.0` → `10300`. Тег должен быть вида `v1.2.3`.

## Как устроено

- `core/`: парсер ссылок и подписок, сборка конфига Xray, хранилище, пинг.
- `vpn/SecurifyVpn`: поднимает TUN и передаёт его fd в Xray (`CoreController.startLoop`). Само приложение исключено из VPN, иначе исходящий трафик Xray зациклится.
- Внешний IP определяется запросом к ip-api.com через отдельный HTTP-инбаунд Xray. Инбаунд слушает unix-сокет в приватной папке приложения, поэтому сторонние приложения не могут им воспользоваться.
- `ui/`: Jetpack Compose и Material 3 Expressive.

## Ограничения

- В Hysteria2 используется первый порт из диапазона (port hopping не поддерживается).
- Shadowsocks с плагинами (obfs, v2ray-plugin) не поддерживается.
- Подписки скачиваются в обход VPN.
- Запрос к ip-api.com идёт по HTTP, без шифрования.

## Лицензии сторонних компонентов

- [Xray-core](https://github.com/XTLS/Xray-core): MPL-2.0.
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite): LGPL-3.0.
- [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded): Apache-2.0.
