# Flikky

**English** | [简体中文](./README.zh-CN.md)

Local-area-network file & message transfer between an Android phone and any browser. Zero install on the receiving side, zero internet required, designed for short-lived ad-hoc sharing.

The phone runs an embedded HTTP server. Any browser on the same Wi-Fi opens the URL printed on the phone, enters a one-shot 6-digit PIN, and a session begins. Text and files flow both ways in real time.

## Status

- **v1.0** — minimum viable loop: start service, browser pairs via URL + PIN, two-way text and file transfer, MD3 chat bubbles, foreground-service notification, security baseline.
- **v1.1** — *released (2026-04-21)* — session archival backed by Room, home session list with pin / rename / delete, read-only history view, crash-recovery on app start, FIFO retention (20 non-pinned) + pinned-not-counted.

See `docs/superpowers/specs/` for design docs and `docs/notes/` for retrospectives, decisions and trap reports.

## Progress

### feat

- [x] Embedded HTTP server bound to the active Wi-Fi IPv4 *(v1.0)*
- [x] URL + single-use PIN pairing, 3-strike lockout, 5-strike service kill *(v1.0)*
- [x] Two-way text & file transfer over HTTP + WebSocket *(v1.0)*
- [x] Foreground service + persistent notification (URL only, never PIN) *(v1.0)*
- [x] MD3 chat bubbles on phone (Jetpack Compose) *(v1.0)*
- [x] Vanilla-JS browser front-end + offline-bundled mdui Web Components *(v1.0)*
- [x] Security baseline: strict CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `HttpOnly` + `SameSite=Strict` cookies, `textContent` only, blob-URL downloads *(v1.0)*
- [x] Room-backed session archive: `SessionEntity` + `MessageEntity` (single table + kind discriminator, `FOREIGN KEY ... ON DELETE CASCADE`) *(v1.1)*
- [x] Home session list with long-press menu (pin / rename / delete) *(v1.1)*
- [x] History screen (read-only timeline per session) *(v1.1)*
- [x] FIFO retention keeps the latest 20 non-pinned sessions; pinned ones don't count toward the quota *(v1.1)*
- [x] Crash-recovery on app start: `finalizeOrphans()` closes dangling sessions, rolls back empty ones, prunes orphaned file dirs *(v1.1)*
- [x] Selectable text bubbles (long-press → system copy/select) *(v1.1)*
- [ ] Multi-session batch export to PC *(v1.2, planned)*
- [ ] Live progress bubble during large-file browser upload *(v1.2, backlog B1)*
- [ ] Message search *(v1.2 / v1.3)*
- [ ] Message recall *(v1.3)*
- [ ] HTTPS with self-signed cert *(v2)*
- [ ] At-rest encryption of local archive *(v2)*

### opt

- [x] Browser UI migrated to mdui Web Components (from hand-rolled CSS) *(v1.0 late)*
- [x] Notification redacts the PIN — lock screen is a physical-world attack surface *(v1.0 late)*
- [x] File storage keyed by `sessionId` (`filesDir/sessions/{id}/files/{fileId}`) so FIFO eviction is just `rm -rf` *(v1.1)*
- [x] Redundant aggregate fields on `SessionEntity` (`messageCount`, `fileCount`, `totalBytes`, `previewText`) to spare the home list from scanning the messages table *(v1.1)*
- [x] Ktor multipart `formFieldLimit` lifted off the 50 MiB default (LAN single-user, disk is the real cap) *(v1.1)*
- [ ] Async tee for phone-pushed files (remove the copy-before-serve latency) *(v1.2, backlog)*
- [ ] Replace browser-side native `alert` with mdui snackbar *(v1.2, backlog B2)*
- [ ] Auto-rebind server after Wi-Fi switch *(post-v1.1)*

### fix

- [x] v1.0-rc1: `staticResources` reading from JVM classpath instead of Android assets; missing `POST_NOTIFICATIONS` runtime request; login page JS not loaded → form default-submits
- [x] v1.1 T8: AGP 9 + built-in Kotlin blocks `kotlin.sourceSets` DSL → `android.disallowKotlinSourceSets=false`
- [x] v1.1 T9/T10: Robolectric 4.14 caps at SDK 33 and no longer transitively pulls `androidx.test:core`
- [x] v1.1 T11: `HomeViewModel(app, repo = ServiceLocator.repository)` crashes `AndroidViewModelFactory` reflection at launch → `@JvmOverloads`
- [x] v1.1 T12: `file_paths.xml` still declared the v1.0 `transfer/` root after v1.1 migrated files to `sessions/{id}/files/` → `FileProvider.getUriForFile` threw, `openFile` now guards with try/catch too
- [x] v1.1 T13: `FileRoutes` POST updated in-memory session only, skipped DB persist → `endSession` treated browser-upload-only sessions as empty and rolled them back (deleting the files too) → added `onPersist` to `fileRoutes`, wired from `KtorServer`
- [x] v1.1 T14: Ktor 3.0 silently caps `receiveMultipart()` at 50 MiB by default → `formFieldLimit = Long.MAX_VALUE`; browser `fetch` now surfaces non-2xx with an alert

## Highlights

- **Zero install on the PC side.** Just a browser. No app store, no extension, no account.
- **Stays on the LAN.** The server binds to the active Wi-Fi IPv4 only — never `0.0.0.0`. No outbound calls.
- **Single-use PIN.** Authentication burns the PIN; subsequent attempts need a new PIN. 3 wrong attempts lock the IP for 30 s; 5 wrong attempts terminate the service.
- **Hardened browser surface.** Strict CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `HttpOnly` + `SameSite=Strict` cookies, `textContent` only (never `innerHTML`), file downloads via blob URLs.
- **Native MD3 UI.** Jetpack Compose on the phone, the [mdui](https://github.com/zdhxiong/mdui) Web Components library on the browser — bundled offline, no CDN.
- **Lockscreen-aware notifications.** The notification surfaces the URL but never the PIN, since the lock screen is a physical-world attack surface.

## Known limitations (documented, not bugs)

- HTTP plaintext (HTTPS self-signed lands in v2).
- Upload throughput is reported as a single end-of-transfer sample (the Ktor 3 multipart API surface didn't expose a streaming `tee`). Live byte-rate during browser → phone uploads will drop to 0 until completion.
- After a Wi-Fi switch the server doesn't auto-rebind; restart the service.
- Non-pinned session retention is hard-coded to the latest 20; the cap is not user-configurable yet (a settings screen is deferred — change the constant and rebuild if you need a different number).

## Tech stack

| Layer            | Choice                                                    |
| ---------------- | --------------------------------------------------------- |
| Language         | Kotlin 2.2                                                |
| Build            | AGP 9 + KSP2 (`2.2.10-2.0.2`)                             |
| Mobile UI        | Jetpack Compose + Material 3                              |
| HTTP server      | Ktor 3 (CIO engine), embedded in a foreground service     |
| WebSocket        | Ktor WebSockets (`pingPeriodMillis = 15_000`)             |
| Persistence      | Room 2.7 (+ KSP2 code generation)                         |
| Browser UI       | Vanilla HTML/CSS/JS + mdui Web Components                 |
| Tests            | JUnit 4 + MockK + Turbine + ktor-server-test-host + Robolectric 4.14 (`@Config(sdk = [33])`) |
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
├── ui/          Compose screens + view models (home, serving, history, components)
├── service/     Foreground service, controller, notifications
├── server/      Ktor server, routes, DTOs, PIN auth
├── session/     In-memory state + message model
├── data/        Room DB, entities, DAOs, SessionRepository, SessionFileStore
├── network/     Wi-Fi IPv4 lookup
├── util/        Pure-Kotlin helpers (no Android dependency)
└── di/          ServiceLocator
app/src/main/assets/web/   Browser front-end (incl. mdui vendored offline)
docs/superpowers/          Design docs, plans, verification checklists
docs/notes/                Retrospectives, traps & fixes, decisions, backlog
```

## Acknowledgements

- [Ktor](https://ktor.io/) — the embedded HTTP/WebSocket engine
- [mdui](https://github.com/zdhxiong/mdui) — Material Design 3 Web Components, bundled offline under MIT

## License

MIT — see [LICENSE](./LICENSE).
