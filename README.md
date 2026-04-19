# Flikky

**English** | [简体中文](./README.zh-CN.md)

Local-area-network file & message transfer between an Android phone and any browser. Zero install on the receiving side, zero internet required, designed for short-lived ad-hoc sharing.

The phone runs an embedded HTTP server. Any browser on the same Wi-Fi opens the URL printed on the phone, enters a one-shot 6-digit PIN, and a session begins. Text and files flow both ways in real time.

## Status

- **v1.0** — minimum viable loop: start service, browser pairs via URL + PIN, two-way text and file transfer, MD3 chat bubbles, foreground-service notification, security baseline.
- **v1.1** — *in progress* — session archival (Room) + home session list with rename / pin / delete.

See `docs/superpowers/specs/` for design docs and `docs/notes/` for retrospectives, decisions and trap reports.

## Highlights

- **Zero install on the PC side.** Just a browser. No app store, no extension, no account.
- **Stays on the LAN.** The server binds to the active Wi-Fi IPv4 only — never `0.0.0.0`. No outbound calls.
- **Single-use PIN.** Authentication burns the PIN; subsequent attempts need a new PIN. 3 wrong attempts lock the IP for 30 s; 5 wrong attempts terminate the service.
- **Hardened browser surface.** Strict CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `HttpOnly` + `SameSite=Strict` cookies, `textContent` only (never `innerHTML`), file downloads via blob URLs.
- **Native MD3 UI.** Jetpack Compose on the phone, the [mdui](https://github.com/zdhxiong/mdui) Web Components library on the browser — bundled offline, no CDN.
- **Lockscreen-aware notifications.** The notification surfaces the URL but never the PIN, since the lock screen is a physical-world attack surface.

## v1 limitations (documented, not bugs)

- HTTP plaintext (HTTPS self-signed lands in v2).
- Messages live in memory only; service stop wipes them. Persistence ships in v1.1.
- Upload throughput is reported as a single end-of-transfer sample (the Ktor 3 multipart API surface didn't expose a streaming `tee`). Live byte-rate during browser → phone uploads will drop to 0 until completion.
- After a Wi-Fi switch the server doesn't auto-rebind; restart the service.

## Tech stack

| Layer            | Choice                                                    |
| ---------------- | --------------------------------------------------------- |
| Language         | Kotlin 2.2                                                |
| Mobile UI        | Jetpack Compose + Material 3                              |
| HTTP server      | Ktor 3 (CIO engine), embedded in a foreground service     |
| WebSocket        | Ktor WebSockets (`pingPeriodMillis = 15_000`)             |
| Browser UI       | Vanilla HTML/CSS/JS + mdui Web Components                 |
| Tests            | JUnit 4 + MockK + Turbine + ktor-server-test-host         |
| Min/Target SDK   | 33 / 36                                                   |
| Single module    | `:app` — no over-modularization                           |

See `docs/notes/decisions.md` for *why* each pick.

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device required)
./gradlew installDebug           # install on a connected device
```

## Project layout

```
app/src/main/java/com/example/flikky/
├── ui/          Compose screens + view models (home, serving, components)
├── service/     Foreground service, controller, notifications
├── server/      Ktor server, routes, DTOs, PIN auth
├── session/     In-memory state + message model
├── network/     Wi-Fi IPv4 lookup
├── util/        Pure-Kotlin helpers (no Android dependency)
└── di/          ServiceLocator
app/src/main/assets/web/   Browser front-end (incl. mdui vendored offline)
docs/superpowers/          Design docs, plans, verification checklists
docs/notes/                Retrospectives, traps & fixes, decisions
```

## Acknowledgements

- [Ktor](https://ktor.io/) — the embedded HTTP/WebSocket engine
- [mdui](https://github.com/zdhxiong/mdui) — Material Design 3 Web Components, bundled offline under MIT

## License

MIT — see [LICENSE](./LICENSE).
