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
- **v1.5.0** — *released (2026-06-08)* — UI/UX overhaul: bottom navigation (Transfer / Settings), a full settings system on DataStore with instant theme switching (Material You dynamic color + 4 warm presets + three-state dark + AMOLED), app/peer preset avatars and in-progress conversation background **synced across both ends** (via a `peer-info` endpoint + `client_hello`), a long-press message action bar (copy / recall / open / delete-with-undo, staggered pop-out), configurable history retention (incl. `0`=keep none, `-1`=unlimited), editable device name, a recall Beta toggle, and the full emoji→Material icon migration with +1-step rounded shapes.
- **v1.5.1** — *released (2026-06-16)* — browser chat-page scroll fix (mdui's fixed top-app-bar injected `padding-top` on `<body>` and fought the `100vh` flex shell → scoped `body.chat-page` overrides + `100dvh`), plus cosmetic cleanup of leftover v1.5.0 minors.
- **v1.6.0** — *released (2026-06-16)* — **conversation experience overhaul**: a context-adaptive top (connection card before pairing → spring-collapse to a slim peer header on connect), a floating message toolbar (tap to summon, long-press for text selection, tap empty to clear) with a settings toggle for a persistent per-bubble action bar, unified equal-corner bubbles (default 18dp) + a corner-radius slider, an avatar-grouping setting (first / last / each), a redesigned input row (text field + add-sheet with file/image cards + circular send) with a second stats row doubling as the snackbar zone, gradient backgrounds dropped in favor of theme-derived solids + a custom-hue slider, a waiting loading indicator, stop-service moved to the header, an "allow back during session" guard (back is intercepted by default; the Settings tab locks while a session runs), and correct edge-to-edge IME insets so the input row sits right above the keyboard. App `versionName`/`versionCode` are now tracked (previously frozen at 1.0/1).
- **v1.7.0** — *released (2026-06-18)* — **home redesign**: the title bar is replaced by a large MD3 SearchBar that expands in place to true fullscreen and searches both session names and message content (FTS), grouped into 会话 / 消息 sections; import moves into an overflow menu. Long-press is the sole entry to multi-select, with a no-checkbox color tri-state selection (`primaryContainer` fill) + selection semantics for TalkBack; an adaptive bottom action bar (pin with smart toggle / rename when single / export / delete) replaces the bottom nav while selecting. The standalone search route is retired. System bars now align with the app (`isNavigationBarContrastEnforced=false` + `isAppearanceLightNavigationBars`).

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
- [x] Import from zip back into the app: `ZipImporter` parse + backward compat for v1.2/v1.3 (replay `nextUniqueName` to resolve paths), name+startedAt duplicate detection, post-import FIFO sweep, import entry + loading dialog + snackbar summary *(v1.4.0)*
- [x] Bottom navigation (Transfer / Settings), `alwaysShowLabel=false` so the unselected tab is icon-only; detail pages auto-hide the bar; per-tab back-stack state preserved *(v1.5.0)*
- [x] Settings system on DataStore with instant theme switching (zero restart): Material You dynamic color + 4 warm presets (coral / mushroom / teal / mist), three-state dark mode + AMOLED override, CompositionLocal golden chain *(v1.5.0)*
- [x] Long-press message action bar: copy / recall (Beta) / open / delete-with-undo, staggered scale-in pop-out below the bubble *(v1.5.0)*
- [x] App & peer preset avatars (12 presets) + in-progress conversation background (default / blank / solid / gradient), **synced across both ends** via `GET /api/peer-info` + WS `client_hello`, plus a browser-side avatar picker *(v1.5.0)*
- [x] User-configurable history retention in settings (default 20, `0` = keep none, `-1` = unlimited); peer avatar id persisted so history shows the correct browser-side avatar *(v1.5.0)*
- [x] emoji → Material icon migration (`material-icons-core` + bundled Symbols vectors), +1-step rounded MD3 shapes, CJK paragraph line-break in bubbles *(v1.5.0)*
- [x] Context-adaptive conversation top: `ConnectionInfoCard` (URL + copy + large PIN) before pairing, spring-collapses to a slim peer header (avatar + name + 已连接 + stop) on connect; shared with the export page *(v1.6.0)*
- [x] Floating message toolbar (default): tap a bubble to summon, long-press for native text selection, tap empty to clear; settings toggle switches to a persistent per-bubble action bar; applied to both active session and history *(v1.6.0)*
- [x] Unified equal-corner bubbles on both ends (default 18dp) + a corner-radius slider in settings (8–28dp) *(v1.6.0)*
- [x] Avatar-grouping setting: show the avatar on the first / last / every message of a same-sender run *(v1.6.0)*
- [x] Redesigned input row: text field + add button (bottom sheet with file / image square cards) + circular up-arrow send, with a second stats row (uptime / files / rate) that also hosts the snackbar *(v1.6.0)*
- [x] Conversation background: gradient removed; theme-derived solid presets + a custom-hue slider clamped to a readable light tone *(v1.6.0)*
- [x] "Allow back during session" setting (default off → back is intercepted with a guiding snackbar); the Settings tab is locked while a transfer session is running *(v1.6.0)*
- [x] Waiting-for-connection loading indicator; stop-service moved into the header *(v1.6.0)*
- [x] Home top bar replaced by a large MD3 `SearchBar` (no title) that expands in place to true fullscreen; searches session names + message content (FTS), grouped into 会话 / 消息; import moved to an overflow menu *(v1.7.0)*
- [x] Long-press is the sole entry to multi-select; no-checkbox color tri-state selection (`primaryContainer` fill) + `selected` / `stateDescription` semantics for TalkBack *(v1.7.0)*
- [x] Adaptive multi-select action bar — pin (smart toggle) / rename (single only) / export / delete (batch); replaces the bottom nav while selecting; standalone search route retired *(v1.7.0)*
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
- [x] Settings persisted via DataStore Preferences; theme flows through a `StateFlow<FlikkySettings>` the `MaterialTheme` observes — theme / dark / AMOLED changes recompose in place, no Activity recreation *(v1.5.0)*
- [x] `peerInfoProvider` reads a `@Volatile` settings snapshot at call time, so it stays correct across Wi-Fi rebind (KtorServer is rebuilt; the lambda survives on a TransferService field) *(v1.5.0)*
- [x] `SessionState.addMessage` keeps the in-memory list timestamp-sorted (binary insert), so an undo-restored message returns to its original position while monotonic new messages still append *(v1.5.0)*
- [x] Correct edge-to-edge IME handling: `adjustResize` + `padding(innerPadding)` + `consumeWindowInsets(innerPadding)` + `imePadding()` so the IME inset is applied exactly once and the input sits right above the keyboard *(v1.6.0)*
- [x] App `versionName` / `versionCode` now tracked (1.6.0 / 10600 via `major*10000+minor*100+patch`) — both were frozen at `1.0` / `1` since the project start, so the installer always showed 1.0 *(v1.6.0)*
- [x] Search session-name and message groups are driven by one debounced query, so they update in lockstep and the "no match" hint never flashes mid-debounce *(v1.7.0)*
- [x] True fullscreen search via per-destination padding (the home destination escapes the top status-bar inset; FAB + bottom nav hidden while expanded), so the SearchBar surface reaches under the status/navigation bars with no side gaps *(v1.7.0)*
- [x] App `versionName` / `versionCode` 1.7.0 / 10700 *(v1.7.0)*

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
- [x] v1.5.0 zip import froze on device: `importSessions` read the retain limit (a DataStore `first()`) inside `withContext(Dispatchers.IO)`, deadlocking DataStore 1.1's single-thread actor. The limit is now read before entering the IO context
- [x] v1.5.0 history count flashed N+1 after a session ended — FIFO sweep only ran on the next service start. Sweep now runs right after `endSession` too, and lowering the retention limit in settings sweeps immediately
- [x] v1.5.0 own-message recall was blocked by a redundant `senderId` check ("只能撤回自己发的消息"); since the UI only ever shows the recall action on own messages, the server-side check was dropped (single-PIN single-user model)
- [x] v1.5.0 undo-delete in an active session dropped the message to the list bottom; `addMessage` now inserts in timestamp order so it returns to its original slot
- [x] v1.5.0 history showed the wrong browser-side avatar (the peer avatar id lived only in memory) — added a `peerAvatarId` column (DB v2→3 migration) persisted on `endSession` and read back in history
- [x] v1.5.0 delete / recall snackbars displaced conversation content and could cover the input bar; they now float as an overlay above the input bar on both phone (Compose overlay) and browser (mdui offset)
- [x] v1.5.1 browser chat page wouldn't scroll: mdui's fixed top-app-bar injected `padding-top` on `<body>`, fighting the `100vh` flex shell → scoped `body.chat-page` overrides + `100dvh`
- [x] v1.6.0 IME inset double-count: without `windowSoftInputMode` the activity panned the window while the column also applied an ime inset, pushing the input to the top with a keyboard-height void; fixed with the canonical `adjustResize` + `consumeWindowInsets` pattern (single application)
- [x] v1.6.0 message input is disabled until a client connects (matching add / send), so there's no editing or keyboard pop in the no-connection state
- [x] v1.6.0 background picker: selecting an option no longer closes the sheet, the custom-hue slider reads back from the current background, and duplicate theme-derived swatches (e.g. coral / mushroom) are de-duplicated
- [x] v1.6.0 connection-card URL stacks above its copy button so long URLs no longer misalign with the icon
- [x] v1.7.0 search expand was not truly fullscreen (FAB / bottom nav showed through, status-bar background unchanged, side gaps); fixed to true edge-to-edge fullscreen
- [x] v1.7.0 system navigation bar / gesture pill background did not match the app color (contrast scrim); fixed with `isNavigationBarContrastEnforced=false` + `isAppearanceLightNavigationBars`
- [x] v1.7.0 search file-hit icon unified to `ic_description` to match the file message bubble

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
- Avatars are preset-only (icon + color); custom images are out of scope. Conversation background offers theme-derived solids + a custom hue (always clamped to a readable light tone); gradients were removed in v1.6.0. *(v1.6.0)*
- The bubble corner-radius slider applies on the phone; the browser uses a static 18px until two-end theme sync lands. *(v1.8.0 planned)*

## Tech stack

| Layer            | Choice                                                    |
| ---------------- | --------------------------------------------------------- |
| Language         | Kotlin 2.2                                                |
| Build            | AGP 9 + KSP2 (`2.2.10-2.0.2`)                             |
| Mobile UI        | Jetpack Compose + Material 3                              |
| HTTP server      | Ktor 3 (CIO engine), embedded in a foreground service     |
| WebSocket        | Ktor WebSockets (`pingPeriodMillis = 15_000`)             |
| Persistence      | Room 2.7 (+ KSP2 code generation)                         |
| Settings store   | DataStore Preferences (instant theme via `StateFlow`)     |
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
├── ui/          Compose screens + view models (home, serving, history, exporting, settings, components)
├── service/     Foreground service, controller, notifications, export notification text
├── server/      Ktor server, routes (incl. ExportRoutes, PeerInfoRoutes), DTOs, PIN auth, ServiceMode
├── session/     In-memory state + message model + NetworkStatus (+ peerAvatarId)
├── data/        Room DB, entities, DAOs, SessionRepository, SessionFileStore, settings (DataStore)
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
