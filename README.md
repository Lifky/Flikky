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
- **v1.8.0** — *released (2026-06-24)* — **design-system & layout pass**: a T-shirt Spacing/Sizes token scale (literals migrated across the UI), the full MD3 type scale plus semantic typography extensions (CJK paragraph line-break), inline shapes replaced by `MaterialTheme.shapes`, shared components extracted (OptionCard / ConfirmDialog / RenameDialog) and nav-bar icons unified to a single drawable source, the Settings screen regrouped into six logical sections with a large title bar + per-row leading icons + an M3 segmented list look, a smoothed in-place SearchBar expand animation flush to the screen edge, and a 600dp content-width cap that centers home / settings / history / serving / exporting on wide screens. *(Released together with v1.9.0; this milestone tag still carries versionName 1.7.0 — the bump landed at the v1.9.0 release.)*
- **v1.9.0** — *released (2026-06-24)* — **session groups**: the home list gains folder-style filter chips (a fixed 「全部」 + custom groups, single-select) backed by a Room v4 migration (`session_groups` table + `sessions.groupId`); the active group is persisted in DataStore and a session is filed into whichever group was active when it started. Inside a group, sessions bucket by 置顶 → 今天 → 昨天 → 更早. Long-pressing a custom chip pops a unified manage dialog (rename / reorder up-down / delete-with-undo); 「全部」 is a virtual, non-deletable, non-movable chip. Multi-select switches to an MD3 floating toolbar (pill, icon-only: pin / rename / **move to group** / export / delete) replacing the full-width bar; **move to group** opens a bottom sheet (custom groups + 「全部」 to ungroup). Settings polish: whole-row radio taps, the bubble-corner Slider on its own full-width line, and duplicate trailing icons dropped from 主题 / 深色模式. Replaces v1.8.0's sort/group chips. App `versionName` / `versionCode` → 1.9.0 / 10900.
- **v1.9.1** — *released (2026-06-24)* — settings-dialog & list polish: single-choice dialogs (dark mode / action style / avatar grouping / history limit) rebuilt as a shared `ChoiceDialog` whose rows are full-width, ≥56dp tall, edge-to-edge with one unified ripple (the radio is indicator-only) — replacing the short, inset rows where only the radio glyph was a hit target; and a fixed gap between a settings row's title/subtitle and its trailing `Switch` so long labels no longer butt against the toggle. App `versionName` / `versionCode` → 1.9.1 / 10901.
- **v1.10.0** — *released (2026-06-26)* — **favorites / ammo-dump**: a new bottom-nav **收藏** tab (Transfer / Favorites / Settings) backed by its own Room v5 migration. Starring a TEXT or completed FILE bubble (from a live session or history) keeps an **independent snapshot copy** — no foreign key to the source, so the favorite survives the source message's deletion, the whole source session's deletion, and FIFO eviction; files are copied into a dedicated favorites dir and opened via FileProvider. Star is reversible (filled / hollow re-render synced across live + history via `(sourceSessionId, sourceMessageId)`); on star a bottom sheet picks a collection (全部 + existing + inline-create). Favorites have their own collections (chip row, long-press manage dialog, isolated search + multi-select) **fully independent** of session groups — deleting a collection re-homes its favorites to 全部 without cascading. This release also unifies the home / favorites / message floating toolbars onto one shared `FlikkyFloatingToolbar` capsule spec, and seeds the message-id counter from the persisted max on startup so ids never collide after a restart. App `versionName` / `versionCode` → 1.10.0 / 11000.
- **v1.10.1** — *released (2026-06-27)* — **favorites quick-send & full icon refresh**: every favorite gains a one-tap send that pushes its snapshot into the live session — from the favorites-page row and from a new conversation-page **弹药箱** `ModalBottomSheet` opened by a ★ entry in the input row — text via `sendText`, files via a new `offerStoredFile` overload streaming the depot copy, gated on `clientConnected` exactly like the session send key, plus a "recent" quick-send row persisting ids (only) in DataStore. The send glyph reuses the session send key's `ic_arrow_upward`, not a paper plane. Separately, all 32 vector drawables migrate to **official Material Symbols** (opsz24 / wght400, verbatim paths under a `translateY(960)` group) — replacing legacy Material Icons and a hand-traced `ic_send_outline` that did not match the official glyph — and the home / favorites selection toolbar floats as a content overlay (lighter capsule, inner Scaffolds no longer double-consume the bottom inset). App `versionName` / `versionCode` → 1.10.1 / 11001.
- **v1.11.0** — *released (2026-06-30)* — **Motion & visual-system overhaul**: the app root adopts `MaterialExpressiveTheme` on **material3 1.5.0-alpha22** with `MotionScheme.expressive()`, and a new `ui/theme/Motion.kt` token layer wraps the official physics springs (spatial / effects × default / fast / slow) behind a **global animation-speed control** (设置 → 动画速度: 关闭 / 慢 / 标准 / 快, persisted in DataStore; 关闭 = reduce-motion, and the system animator-duration-scale is always honored). On that foundation: MD3 navigation transitions (tab fade-through + push/pop shared-axis), a **predictive-back** gesture, list add/remove/reorder animations (`animateItem`), a spatial-spring height morph for the serving connection header, the floating toolbars migrated to the official `HorizontalFloatingToolbar`, and inline tween/spring literals tokenized. **Color**: the 4 warm presets are replaced by **8 custom MTB themes** (淡曙红 / 丹紫红 / 橙皮黄 / 秋葵黄 / 安安蓝 / 珠母灰 / 鹦鹉绿 / 芥花紫), each with full light/dark × standard/medium/high-contrast role maps, a **contrast-level** setting (跟随系统 / 标准 / 中 / 高, system contrast read via `UiModeManager`), and **two-end color sync** — the phone's active theme seed + dark state push to the browser, which re-derives a matching mdui palette (shared Material Color Utilities seed → same hue). **Lists**: settings, transfer and favorites rows migrate to the official M3 Expressive `SegmentedListItem` (`segmentedShapes` corners + `SegmentedGap`), with the built-in selection spring on multi-select and a `surfaceContainer` container that finally gives the settings list light-mode hierarchy. The three waiting-to-connect actions (复制地址 + two 停止服务) gain filled-tonal backgrounds. App `versionName` / `versionCode` → 1.11.0 / 11100.
- **v1.12.0** — *released (2026-07-02)* — **shared design tokens & the avatar system**: a single design-token source of truth (`tokens.css`, generated from the App's Kotlin token constants) now backs the browser — `app.css` shape / spacing / type literals are tokenized to `var()`, the **bubble corner-radius syncs phone → browser** over `peer-info`, and an in-session **quick-settings** panel adjusts bubble corner + dark mode live. The browser front-end migrates off hand-rolled markup onto **official mdui / Material Symbols** components (inline-SVG icons sharing the App's exact paths, an `mdui-dialog` avatar picker, `mdui-menu-item` recall menu). **Avatars** are reworked end to end: a string `AvatarKey` model (`icon:name:filled|outline` / `char:*`) with themed `secondaryContainer` backgrounds, a **fill** switch backed by the full Material Symbols **variable font** (`FILL` / `wght` / `GRAD` / `opsz` axes) bundled for both ends, character avatars, an App-side picker that can set the **browser's** avatar (synced via WS `peer_avatar_changed` + a `sessions.peerAvatarKey` Room v6 migration so History restores the real avatar), the **avatar-grouping** mode (first / last / each) pushed live to the browser and added to the in-session quick settings, and the Web PIN page syncing the App's active theme **before** auth via a public `/api/web-theme`. Plus fixes: serving messages auto-scroll to the latest, the browser default-background watermark tracks connection state on service stop, partial appearance payloads no longer reset the phone avatar, grouped avatars reflow after recall, and the avatar picker's selection outline + mobile-UA input row no longer clip. App `versionName` / `versionCode` → 1.12.0 / 11200.

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
- [x] Design-system tokens: a T-shirt Spacing scale + Sizes tokens, the full MD3 type scale + semantic typography extensions, inline shapes replaced by `MaterialTheme.shapes` *(v1.8.0)*
- [x] Settings regrouped into six logical sections with a large MD3 title bar, per-row leading icons, and an M3 segmented list look *(v1.8.0)*
- [x] 600dp content-width cap centering home / settings / history / serving / exporting on wide screens *(v1.8.0)*
- [x] Session groups: folder-style filter chips (fixed 「全部」 + custom groups, single-select) backed by a Room v4 `session_groups` migration; the active group persists in DataStore and a session is filed into whichever group was active at start; in-group bucketing 置顶 / 今天 / 昨天 / 更早 *(v1.9.0)*
- [x] Long-press a custom chip → unified manage dialog (rename / reorder up-down / delete-with-undo); 「全部」 is a virtual non-deletable / non-movable chip *(v1.9.0)*
- [x] Multi-select floating toolbar (MD3 pill, icon-only) with a **move-to-group** action → bottom sheet (custom groups + 「全部」 to ungroup), batch-rebinding `groupId` in one update *(v1.9.0)*
- [x] Global animation-speed control (关闭 / 慢 / 标准 / 快) in settings, persisted in DataStore and threaded through a `Motion` token layer over `MotionScheme.expressive()`; 关闭 = reduce-motion, and the system animator-duration-scale is always honored *(v1.11.0)*
- [x] MD3 navigation motion: tab switches fade-through, route push/pop use shared-axis (opposite directions forward / back); a predictive-back gesture drives route pop + sheet / multi-select dismiss *(v1.11.0)*
- [x] List add / remove / reorder animations via the official `animateItem` (spatial spring for placement, effects spring for fade) across home / serving / favorites / history *(v1.11.0)*
- [x] 8 custom MTB themes (淡曙红 / 丹紫红 / 橙皮黄 / 秋葵黄 / 安安蓝 / 珠母灰 / 鹦鹉绿 / 芥花紫) replacing the 4 warm presets, each with full light/dark × standard/medium/high-contrast role maps; a contrast-level setting (跟随系统 / 标准 / 中 / 高, system contrast via `UiModeManager`) *(v1.11.0)*
- [x] Two-end color sync: the phone's active theme seed + dark state push to the browser over `peer-info`, which re-derives a matching mdui palette (shared Material Color Utilities seed → same hue) *(v1.11.0)*
- [x] Settings / transfer / favorites lists migrated to the official M3 Expressive `SegmentedListItem` (`segmentedShapes` + `SegmentedGap`), with the built-in selection spring on multi-select and a `surfaceContainer` container for light-mode hierarchy *(v1.11.0)*
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
- [x] Whole-row radio taps in settings dialogs (`selectable(role=RadioButton)` over the row inside a `selectableGroup`), instead of only the radio glyph being a hit target *(v1.9.0)*
- [x] `SettingItem` gains an optional full-width content slot; the bubble-corner Slider moves there to span the row instead of a cramped 160dp; duplicate trailing icons dropped from 主题 / 深色模式 *(v1.9.0)*
- [x] material3 1.4.0 stable keeps `HorizontalFloatingToolbar` behind an internal `ExperimentalMaterial3ExpressiveApi`, so the floating toolbar is built to the same MD3 spec with stable components (pill `Surface` + `IconButton`) *(v1.9.0)*
- [x] App `versionName` / `versionCode` 1.9.0 / 10900 *(v1.9.0)*
- [x] Favorites / ammo-dump: a 收藏 bottom-nav tab backed by a Room v5 migration; star a TEXT or completed FILE bubble to keep an **independent snapshot copy** (no FK to source, files copied to a favorites dir, opened via FileProvider) that survives source-message / source-session deletion and FIFO eviction *(v1.10.0)*
- [x] Reversible star synced across live session + history via `(sourceSessionId, sourceMessageId)`; on star a bottom sheet picks a collection (全部 + existing + inline-create) *(v1.10.0)*
- [x] Favorites collections (chip row, long-press manage dialog, isolated search + multi-select) fully independent of session groups; deleting a collection re-homes its favorites to 全部 without cascading *(v1.10.0)*
- [x] Shared `FlikkyFloatingToolbar` capsule unifies the home / favorites / message floating toolbars onto one MD3 spec *(v1.10.0)*
- [x] Message-id counter seeded from the persisted max on startup so ids never collide after a restart *(v1.10.0)*
- [x] App `versionName` / `versionCode` 1.10.0 / 11000 *(v1.10.0)*
- [x] Favorites quick-send: one-tap send on every favorite (favorites-page row + a conversation-page 弹药箱 bottom sheet via a ★ input-row entry) pushes the snapshot into the live session (text → `sendText`, file → a new `offerStoredFile` overload), gated on `clientConnected`; a "recent" row persists ids in DataStore *(v1.10.1)*
- [x] All 32 vector drawables migrated to official Material Symbols (opsz24 / wght400, verbatim paths under a `translateY(960)` group) — replacing legacy Material Icons + a hand-traced `ic_send_outline` *(v1.10.1)*
- [x] App `versionName` / `versionCode` 1.10.1 / 11001 *(v1.10.1)*
- [x] `ui/theme/Motion.kt` is a thin adapter over the official `MaterialTheme.motionScheme` (spatial / effects × default / fast / slow) scaled by a `LocalMotionScale` = min(user speed, system animator-duration-scale); inline tween/spring literals tokenized; `LocalClipboardManager` → `LocalClipboard` *(v1.11.0)*
- [x] Floating toolbars migrated from a hand-rolled `Surface` + `Row` to the official `HorizontalFloatingToolbar` (material3 1.5.0-alpha22, `ExperimentalMaterial3ExpressiveApi`); the serving connection header height morphs with a spatial spring *(v1.11.0)*
- [x] The three waiting-to-connect actions (复制地址 + two 停止服务) gain filled-tonal backgrounds; 停止服务 keeps its danger semantics via an errorContainer tonal scheme *(v1.11.0)*
- [x] App `versionName` / `versionCode` 1.11.0 / 11100 *(v1.11.0)*

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
- [x] v1.10.1 favorites send affordance used a paper plane (the nav transfer-tab glyph); switched to the session send key's `ic_arrow_upward`, and the favorites-page send gate unified to `clientConnected` (was `currentSessionId != null`)
- [x] v1.10.1 home / favorites selection toolbar now floats as a content overlay (lighter capsule); inner Scaffolds no longer double-consume the bottom inset

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
- Avatars support Material Symbol icons (filled / outline) and single characters, on themed backgrounds; custom images are out of scope. Conversation background offers theme-derived solids + a custom hue (always clamped to a readable light tone); gradients were removed in v1.6.0. *(v1.12.0)*
- The bubble corner-radius and the avatar-grouping mode now sync phone → browser (v1.12.0); two-end **color** theme sync (seed + light/dark) landed in v1.11.0. *(v1.12.0)*
- Two-end color sync covers hue + light/dark only: the browser doesn't receive the contrast level, and under Material You dynamic color (no wallpaper access) it falls back to mdui's default palette following just the dark state; the export snapshot page isn't themed. *(v1.11.0)*

## Tech stack

| Layer            | Choice                                                    |
| ---------------- | --------------------------------------------------------- |
| Language         | Kotlin 2.2                                                |
| Build            | AGP 9 + KSP2 (`2.2.10-2.0.2`)                             |
| Mobile UI        | Jetpack Compose + Material 3 Expressive (material3 1.5.0-alpha22, `MaterialExpressiveTheme` + `MotionScheme`) |
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
