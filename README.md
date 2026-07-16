<p align="center">
  <img src="./app/src/main/ic_launcher-playstore.png" width="128" alt="Flikky logo">
</p>

<h1 align="center">Flikky</h1>

<p align="center">
  Account-free LAN transfer between an Android phone and any modern browser.
</p>

<p align="center">
  <strong>English</strong> · <a href="./README.zh-CN.md">简体中文</a>
</p>

Flikky turns an Android phone into a short-lived local file server. A browser on the same Wi-Fi opens the address shown by the app and can exchange text and files in real time. The browser side needs no app, extension, account, cloud service, or internet connection.

Flikky is designed for trusted local networks and keeps the operational complexity on the phone: pairing, session state, history, favorites, backup, and recovery all live in the Android app.

## Status

| Channel | Revision | State |
| --- | --- | --- |
| Stable source | [`v1.13.0`](https://github.com/Lifky/Flikky/tree/v1.13.0) · 2026-07-05 | Optional PIN authentication, local favorites, and the complete transfer/history experience described below. |
| `main` | [Unreleased changes](https://github.com/Lifky/Flikky/compare/v1.13.0...main) | Full ZIP backup scopes, local or browser export destinations, contextual archive actions, and current UI fixes. |

Use the stable tag for a reproducible build. Use `main` when evaluating the latest unreleased work. Version history is available from the repository's [tags](https://github.com/Lifky/Flikky/tags).

## Use Flikky

1. Install Flikky on a phone running Android 13 or newer.
2. Connect the phone and the receiving device to the same Wi-Fi network.
3. Start the transfer service in Flikky. The app shows a local URL and, by default, a one-time six-digit PIN.
4. Open the URL in the browser and enter the PIN when prompted.
5. Send text or files in either direction. Progress, connection state, and failures update in real time.
6. Stop the service when finished. The completed session remains available in History according to the configured retention policy.

The network must allow device-to-device traffic. Guest Wi-Fi and access points with client isolation can block the connection even when both devices show the same network name.

## Capabilities

- **Two-way transfer:** text and files move between Android and the browser over HTTP and WebSocket, with progress and failure states for both directions.
- **Session history:** Room-backed sessions support search, pin, rename, grouping, per-message actions, configurable retention, and crash recovery.
- **Recall and cleanup:** messages can be recalled during an active session; local history items and sessions can be deleted with confirmation or undo where appropriate.
- **Favorites:** keep independent text or file snapshots in collections, add local items without a session, search them, and send them back into an active transfer.
- **Portable archives:** export sessions, favorites, settings, or all data to a ZIP archive; current `main` can save it on Android or serve it to a browser, then import it later.
- **Adaptive appearance:** Material 3 Expressive themes, dark mode, contrast, motion speed, avatars, bubble shape, grouping, and selected appearance settings stay aligned across phone and browser.
- **Offline browser client:** the HTML, CSS, JavaScript, mdui components, Material Symbols font, and design tokens are bundled in the APK; no CDN is used.

## Security Model and Limits

Flikky reduces exposure, but it does not turn an untrusted LAN into a secure transport.

- The server binds only to the active Wi-Fi IPv4 address, never `0.0.0.0`, and does not depend on a cloud backend.
- PIN authentication is enabled by default. A PIN is single-use; three wrong attempts lock the source IP for 30 seconds and five wrong attempts stop the service.
- PIN authentication can be disabled in Settings. When disabled, anyone who can reach the phone on the same LAN can open the service.
- Browser responses use a strict CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, HttpOnly/SameSite cookies, `textContent` rendering, and short-lived Blob download URLs.
- Notifications show the connection URL but never expose the PIN or token on the lock screen.

Known boundaries:

- **Traffic is plaintext HTTP.** Another party able to inspect traffic on the LAN can read transferred content. Do not use Flikky for sensitive data on a hostile or shared network.
- **Browser extensions are outside the trust boundary.** An extension with page access can read the DOM despite CSP. Use a clean browser profile or a private window with extensions disabled for sensitive transfers.
- **Local data is not encrypted at rest.** Room data, stored files, favorites, and exported ZIP archives rely on Android device protection and the destination storage provider.
- Changing to a Wi-Fi network with a different IP ends the current browser connection. Reopen the new URL shown by the app.

HTTPS and encrypted local archives remain future major-version work.

## Build from Source

Prerequisites:

- JDK 17
- Android SDK Platform 37
- An Android 13+ device or emulator for installation and instrumented tests

```bash
# Build the debug APK and run JVM tests
./gradlew assembleDebug testDebugUnitTest

# Install on a connected device
./gradlew installDebug

# Run instrumented tests on a connected device/emulator
./gradlew connectedAndroidTest
```

On Windows PowerShell, point `JAVA_HOME` to JDK 17 and use the wrapper batch file:

```powershell
$env:JAVA_HOME = '<path-to-jdk-17>'
.\gradlew.bat assembleDebug testDebugUnitTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Browser regression checks use Node.js without third-party packages:

```bash
node --check app/src/main/assets/web/app.js
node --test app/src/test/web/app-avatar-default.test.js
node scripts/test-web-avatar-reflow.js
node scripts/test-web-login-theme.js
```

## Architecture

```text
Android app
├── Jetpack Compose UI
├── TransferService (foreground-service lifecycle)
│   └── Ktor CIO server ── HTTP/WebSocket ── Browser client
├── Room + app-owned files (sessions and favorites)
└── DataStore Preferences (settings)
```

| Path | Responsibility |
| --- | --- |
| `ui/` | Compose screens, ViewModels, shared components, and theme |
| `service/` | Foreground service, transfer controller, and notifications |
| `server/` | Ktor server, routes, DTOs, authentication, and WebSocket hub |
| `session/` | In-memory session state and message models |
| `data/` | Room database, repositories, file stores, and settings persistence |
| `export/` | ZIP schema, importer/exporter, snapshots, and file naming |
| `network/` | Wi-Fi IPv4 discovery and network rebind handling |
| `util/`, `di/` | Pure helpers and dependency wiring |
| `app/src/main/assets/web/` | Browser application bundled into the APK |

The project intentionally remains a single Android `:app` module. Android-specific dependencies stay out of the server and pure-logic boundaries so core behavior remains testable on the JVM.

## Contributing

- Keep changes focused and include regression coverage for behavior changes.
- Run `assembleDebug` and `testDebugUnitTest` before committing; run relevant browser checks when changing web assets.
- Preserve the network and browser security invariants described above. Never commit secrets or weaken them merely to make a test pass.
- Keep Android `Context` out of `server/`; inject platform behavior through interfaces or providers.
- Objects that survive a Wi-Fi rebind must resolve current Ktor-owned dependencies at call time instead of retaining an obsolete server instance.

## Acknowledgements

- [Ktor](https://ktor.io/) for the embedded HTTP/WebSocket server
- [mdui](https://github.com/zdhxiong/mdui) for the offline-bundled Material Design 3 Web Components

## License

MIT. See [LICENSE](./LICENSE).
