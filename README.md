# Flikky

**English** | [简体中文](./README.zh-CN.md)

Local-area-network file & message transfer between an Android phone and any browser. Zero install on the receiving side, zero internet required, designed for short-lived ad-hoc sharing.

The phone runs an embedded HTTP server. Any browser on the same Wi-Fi opens the URL printed on the phone, enters a one-shot 6-digit PIN, and a session begins. Text and files flow both ways in real time.

## Status

- **v1.0** — minimum viable loop: start service, browser pairs via URL + PIN, two-way text and file transfer, MD3 chat bubbles, foreground-service notification, security baseline.
- **v1.1** — *released (2026-04-21)* — session archival backed by Room, home session list with pin / rename / delete, read-only history view, crash-recovery on app start, FIFO retention (20 non-pinned) + pinned-not-counted.
- **v1.2** — *released (2026-05-13)* — multi-session batch export to PC as a streaming zip, live upload-progress bubbles, mdui snackbar feedback, Wi-Fi auto-rebind with status banner, in-progress-session affordances on home (resume / inline stop), WS app-layer heartbeat + server-stopped event for clean disconnects.
- **v1.3** — *released (2026-05-24)* — cross-session message search (FTS4 + LIKE fallback), message recall in active sessions (hard-delete + dual-side confirm + instant sync), per-message delete in history, app-layer ping/pong replacing passive frame timeout, closure-capture audit with regression guard, export-page health detection via WS + fetch probe.
- **v1.4.0** — *released (2026-06-04)* — async file transfer (immediate bidirectional IN_PROGRESS bubbles + progress bars + failure feedback + interrupted-upload cleanup), import from zip back into the app (backward-compatible with v1.2/v1.3 format + duplicate detection + post-import FIFO sweep), export `relativePath` dedup fix (messages.json matches zip entries, version bumped to 1.4).

Design docs are kept in a local-only `docs/others/` tree; the public repo carries only the source.

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
- [x] Multi-session batch export to PC as streaming zip (`messages.txt` + `messages.json` + `files/`) *(v1.2)*
- [x] `/api/export/info` summary endpoint + browser `/export` page (mdui-styled) *(v1.2)*
- [x] Post-export Keep-local / Delete-local choice with AlertDialog confirmation *(v1.2)*
- [x] Live progress bubbles for browser uploads (XHR `upload.onprogress` + WS dedup via X-Client-Id) *(v1.2)*
- [x] Wi-Fi auto-rebind: `ConnectivityManager.NetworkCallback` → stop / restart Ktor, banner reports Lost / Switching / Switched *(v1.2)*
- [x] In-progress session affordances on home: tap to resume + inline 停止 button + FAB switches to 继续服务 *(v1.2)*
- [x] WS app-layer heartbeat (4-second frame timeout) + `server_stopped` event so the browser distinguishes user-stop from network outage *(v1.2)*
- [x] Cross-session message search: FTS4 full-text index + LIKE fallback for CJK, search screen with debounced input, hit-to-history jump with scroll + highlight *(v1.3)*
- [x] Message recall in active sessions: long-press → confirm dialog → hard-delete + both-side instant removal + snackbar notification; per-message delete in history *(v1.3)*
- [x] App-layer ping/pong + 2-second frame timeout: instant disconnect detection (~2s), replaces v1.2's passive-only heartbeat *(v1.3)*
- [x] Closure-capture systematic audit: TransferControllerRebindReferenceTest regression guard + CLAUDE.md convention *(v1.3)*
- [x] Export-page health detection: WS connection (ping/pong) + fetch probe, cancel-export dialog, download-started cleanup *(v1.3)*
- [x] `senderId` end-to-end: `phone-{ANDROID_ID}` stable across restarts, browser `X-Client-Id` per session; recall authorization by sender match *(v1.3)*
- [x] Async file transfer: both directions broadcast an immediate IN_PROGRESS bubble + 5%-interval progress + `file_ready`/`file_removed` events; failure shows `传输失败`/`发送失败` feedback *(v1.4.0)*
- [x] Import from zip back into the app: `ZipImporter` parse + backward compat for v1.2/v1.3 (replay `nextUniqueName` to resolve paths), name+startedAt duplicate detection, post-import FIFO sweep, 📥 entry + loading dialog + snackbar summary *(v1.4.0)*
- [ ] HTTPS with self-signed cert *(v2)*
- [ ] At-rest encryption of local archive *(v2)*

### opt

- [x] Browser UI migrated to mdui Web Components (from hand-rolled CSS) *(v1.0 late)*
- [x] Notification redacts the PIN — lock screen is a physical-world attack surface *(v1.0 late)*
- [x] File storage keyed by `sessionId` (`filesDir/sessions/{id}/files/{fileId}`) so FIFO eviction is just `rm -rf` *(v1.1)*
- [x] Redundant aggregate fields on `SessionEntity` (`messageCount`, `fileCount`, `totalBytes`, `previewText`) to spare the home list from scanning the messages table *(v1.1)*
- [x] Ktor multipart `formFieldLimit` lifted off the 50 MiB default (LAN single-user, disk is the real cap) *(v1.1)*
- [x] Replace browser-side native `alert` with mdui snackbar (`window.flikky.showError/showInfo`) *(v1.2)*
- [x] Notification text refreshed on every Wi-Fi rebind so the user sees the current IP *(v1.2)*
- [x] `/api/messages` returns a timestamp-sorted `ordered` view so reload preserves chronological order across text/file kinds *(v1.2)*
- [x] `MAX_RECONNECT_ATTEMPTS` ceiling + heartbeat-driven dead-WS detection so flaky links don't loop forever *(v1.2)*
- [x] Browser disconnect UI fires immediately on frame timeout (not waiting for TCP close) *(v1.3)*
- [x] File download `Content-Disposition` uses original filename instead of UUID *(v1.3)*
- [x] Async tee for phone-pushed files: broadcast IN_PROGRESS immediately + copy in a background coroutine with progress, removing the synchronous copy-before-serve block *(v1.4.0)*

### fix

- [x] v1.0-rc1: `staticResources` reading from JVM classpath instead of Android assets; missing `POST_NOTIFICATIONS` runtime request; login page JS not loaded → form default-submits
- [x] v1.1 T8: AGP 9 + built-in Kotlin blocks `kotlin.sourceSets` DSL → `android.disallowKotlinSourceSets=false`
- [x] v1.1 T9/T10: Robolectric 4.14 caps at SDK 33 and no longer transitively pulls `androidx.test:core`
- [x] v1.1 T11: `HomeViewModel(app, repo = ServiceLocator.repository)` crashes `AndroidViewModelFactory` reflection at launch → `@JvmOverloads`
- [x] v1.1 T12: `file_paths.xml` still declared the v1.0 `transfer/` root after v1.1 migrated files to `sessions/{id}/files/` → `FileProvider.getUriForFile` threw, `openFile` now guards with try/catch too
- [x] v1.1 T13: `FileRoutes` POST updated in-memory session only, skipped DB persist → `endSession` treated browser-upload-only sessions as empty and rolled them back (deleting the files too) → added `onPersist` to `fileRoutes`, wired from `KtorServer`
- [x] v1.1 T14: Ktor 3.0 silently caps `receiveMultipart()` at 50 MiB by default → `formFieldLimit = Long.MAX_VALUE`; browser `fetch` now surfaces non-2xx with an alert
- [x] v1.2 ServiceLocator.reset replacing instances → HomeViewModel cached dead refs → crash on 2nd export + post-stop UI thought a transfer was still running. Reset now reuses the instance and clears its state in place
- [x] v1.2 `ForegroundServiceDidNotStartInTime` whenever an early-return path skipped `startForeground()`. Every `onStartCommand` branch now posts a transient foreground notification first, then either replaces it or `stopForeground + stopSelf`
- [x] v1.2 browser landed on `/app` after PIN even in Export mode. Auth response now carries `redirectTo` per ServiceMode
- [x] v1.2 file upload bubbles duplicated because the uploader saw its own WS broadcast. POST routes propagate `X-Client-Id` into the broadcast payload; the browser skips events whose `senderId` matches its own
- [x] v1.2 closure-capture class of bugs: `statusBroadcastJob` and `TransferController.wsHub` both held the *original* `KtorServer.wsHub` after a rebind, so APP→browser broadcasts went to a dead hub. Both call sites now resolve `ktor?.wsHub` at broadcast time
- [x] v1.2 browser thought a half-open WebSocket was alive (OS hadn't torn the TCP yet, `readyState` still `OPEN`). Added an app-layer 4-second frame timeout; missing frames force-close and trigger reconnect
- [x] v1.2 user-initiated stop kept the browser in reconnect loop. Server broadcasts a `server_stopped` event before closing each WS; browser flips a flag and skips the reconnect timer; safety net: `MAX_RECONNECT_ATTEMPTS = 6`
- [x] v1.3 FTS4 `categories='L* N* Co'` crashes on Android SQLite (no ICU). Dropped to `remove_diacritics=1` only; CJK search via LIKE fallback
- [x] v1.3 recall reworked from History soft-delete + placeholder to ServingScreen hard-delete + instant removal + dual-side confirm dialog
- [x] v1.3 `ws.close()` on half-open TCP blocks until TCP timeout (30-60s), delaying UI feedback. Heartbeat now updates UI *before* close; reconnect starts immediately
- [x] v1.3 export WS used frame-timeout but export mode has no status broadcast → instant disconnect loop. Switched to ping/pong for export WS
- [x] v1.3 export page download/cancel didn't stop WS → reconnect loop after server shutdown. Download and `server_stopped` now close WS + stop probe
- [x] v1.3 file download saved with UUID filename instead of original name. `Content-Disposition` now reads original name from session messages
- [x] v1.4.0 phone couldn't see a bubble while the browser uploaded a large file. `FileRoutes` POST now creates the IN_PROGRESS message as soon as the file-part header arrives, streams with progress, then flips to COMPLETED
- [x] v1.4.0 interrupted uploads (browser refresh / WiFi drop) left a stuck IN_PROGRESS message. The multipart receive is wrapped in try-catch: on disconnect it marks FAILED, deletes the partial file, broadcasts `file_removed`
- [x] v1.4.0 WiFi drop gave slow upload feedback. Browser tracks `activeUploads[]` and `enterDisconnected` aborts every in-flight XHR immediately instead of waiting for TCP timeout
- [x] v1.4.0 phone-sent files couldn't be opened in History. Dropped the `origin==BROWSER` restriction; every COMPLETED file is now openable
- [x] v1.4.0 export `messages.json` `relativePath` didn't match the actual zip entry name (v1.2 carry-over). `ZipExporter` now computes dedup names before handing them to the formatter
- [x] v1.4.0 phone "attach" button stayed clickable after WiFi drop. Now gated on `ui.clientConnected` like the send button

## Highlights

- **Zero install on the PC side.** Just a browser. No app store, no extension, no account.
- **Stays on the LAN.** The server binds to the active Wi-Fi IPv4 only — never `0.0.0.0`. No outbound calls.
- **Single-use PIN.** Authentication burns the PIN; subsequent attempts need a new PIN. 3 wrong attempts lock the IP for 30 s; 5 wrong attempts terminate the service.
- **Hardened browser surface.** Strict CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `HttpOnly` + `SameSite=Strict` cookies, `textContent` only (never `innerHTML`), file downloads via blob URLs.
- **Native MD3 UI.** Jetpack Compose on the phone, the [mdui](https://github.com/zdhxiong/mdui) Web Components library on the browser — bundled offline, no CDN.
- **Lockscreen-aware notifications.** The notification surfaces the URL but never the PIN, since the lock screen is a physical-world attack surface.

## Known limitations (documented, not bugs)

- HTTP plaintext (HTTPS self-signed lands in v2).
- Wi-Fi switch (different IP) tears down the in-flight WS — the browser must reopen the new URL shown in the banner. Same-IP recoveries auto-reconnect within a few seconds. *(v1.2)*
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

See `docs/others/notes/decisions.md` for *why* each pick.

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
├── ui/          Compose screens + view models (home, serving, history, exporting, components)
├── service/     Foreground service, controller, notifications, export notification text
├── server/      Ktor server, routes (incl. ExportRoutes), DTOs, PIN auth, ServiceMode
├── session/     In-memory state + message model + NetworkStatus
├── data/        Room DB, entities, DAOs, SessionRepository, SessionFileStore
├── export/      ExportSession / ExportMode / ExportSnapshot / ZipExporter / formatters
├── network/     Wi-Fi IPv4 lookup, NetworkRebinder (rebind intent state machine)
├── util/        Pure-Kotlin helpers (no Android dependency)
└── di/          ServiceLocator
app/src/main/assets/web/   Browser front-end (incl. mdui vendored offline, export.html, snackbar.js)
docs/others/                Local-only design/retrospective/checklist directory (gitignored)
```

## Acknowledgements

- [Ktor](https://ktor.io/) — the embedded HTTP/WebSocket engine
- [mdui](https://github.com/zdhxiong/mdui) — Material Design 3 Web Components, bundled offline under MIT

## License

MIT — see [LICENSE](./LICENSE).
