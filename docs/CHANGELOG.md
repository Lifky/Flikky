# Changelog

**English** · [简体中文](./CHANGELOG.zh-CN.md)

This file records user-facing changes for each Flikky release, loosely following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow `x.y.z`: x for major architectural changes, y for new features, z for bug fixes. Dates are tag creation dates.

## [v1.16.0](https://github.com/Lifky/Flikky/releases/tag/v1.16.0) · 2026-08-03

### Added
- Files overview: browse files across all sessions with direction/category filters, search, and sorting; multi-select supports favorite, save, share, jump to message, and delete
- File deletion: remove a file's on-disk copy while History keeps an inert "deleted" record; the deleted state carries through export archives, and deleted blobs are skipped on export
- Drag-and-drop file upload in the browser client, with an overlay and a folder guard
- Import conflict handling: when imported sessions already exist locally, choose to skip them or overwrite them with the archive version

### Changed
- File list subtitles drop the session name so size and date stay visible
- File bubble tap hint follows the message action style ("Tap for actions" under the floating toolbar)
- Files screen search moved into an outlined field in the top bar; sorting uses the filter-list icon; shorter home search placeholder

### Fixed
- Floating toolbar: shadow is no longer clipped during the show/hide animation, elevation matches MD3 level 3, and the list lifts its bottom padding while the toolbar is shown
- Deleting a message is now always committed to the database; previously, leaving the screen within the undo window could resurrect it in History
- Deleted file messages can summon the floating toolbar in History
- Inline action buttons stay visible after their label changes (favorite → unfavorite, multi-file completion)
- Browser drop overlay stays hidden until a drag actually enters the page
- Search bar container color aligned with list items and the nav bar

## [v1.15.0](https://github.com/Lifky/Flikky/releases/tag/v1.15.0) · 2026-07-23

### Added
- Full English localization for the app and the browser client; the language setting syncs between phone and browser
- Starting with this release, a signed APK is published on GitHub Releases (`Flikky_{version}_release.apk`)

### Changed
- Default theme changed to Danshu red
- Unified empty-state typography (no sessions / no favorites, etc.)
- Smoother language switching (declared the `screenLayout` config change to eliminate the switch flash)
- Improved PIN copying; removed the redundant privacy tip on the PIN login page

### Fixed
- Theme and avatar defaults in exported archives now match the app's actual defaults
- Settings export no longer contains beta-stage internal names

## [v1.14.0](https://github.com/Lifky/Flikky/tree/v1.14.0) · 2026-07-16

### Added
- Full backup scopes: sessions, favorites, settings, or all data can be exported and re-imported (ZIP schema v2)
- Local Android storage as an export destination (previously browser download only)
- Contextual archive actions: start a matching-scope export directly from the session/favorites screens

### Changed
- The "allow recall" setting is enforced on both phone and browser
- Polish pass on labels, avatars, and History actions alignment

## [v1.13.0](https://github.com/Lifky/Flikky/tree/v1.13.0) · 2026-07-05

### Added
- PIN authentication can be disabled in Settings (still on by default; when off, anyone on the same LAN can connect — see the security model in the README)
- Add local text/file favorites without an active session

### Changed
- Centered setting-row accessories; polish on the add-text-favorite sheet

## [v1.12.0](https://github.com/Lifky/Flikky/tree/v1.12.0) · 2026-07-02

### Added
- Shared design tokens across both ends: `tokens.css` is generated from app-side Kotlin constants — a single source of truth for shape/spacing/type
- Reworked avatar system: preset icon avatars, filled icon avatars, single-character avatars; Material Symbols variable font bundled offline
- In-session quick settings: bubble corner radius and dark mode adjustable from the transfer screen
- Appearance settings such as bubble corners and avatar grouping sync to the browser via peer-info
- Browser recall menu and avatar picker rebuilt on official mdui components

### Fixed
- Login page applies the phone theme before authentication
- Browser watermark updates correctly after the service stops
- Grouped avatars reflow correctly after a recall; grouping updates no longer drop the phone avatar
- Transfer screen auto-scrolls to the latest message

## [v1.11.0](https://github.com/Lifky/Flikky/tree/v1.11.0) · 2026-06-30

### Added
- M3 Expressive Motion throughout: screen transitions (fade-through / shared-axis), predictive back gesture, list add/remove/reorder animations, nav bar and FAB show/hide animations
- Global animation speed setting: off / slow / standard / fast
- 8 custom preset themes (replacing the previous 4) with contrast levels
- Browser theme aligns with the phone's active theme in real time

### Changed
- Home/favorites/settings lists migrated to the official M3 Expressive segmented list component with selection springs
- Floating toolbar migrated to the official `HorizontalFloatingToolbar`
- The three waiting-screen actions switched to filled-tonal style

### Fixed
- Crash when the search bar side-padding spring overshot to a negative value

## [v1.10.1](https://github.com/Lifky/Flikky/tree/v1.10.1) · 2026-06-27

### Added
- Quick-send favorites inside a session: bottom sheet with recently used (5 items), quick search, and collection switching

### Changed
- All icon drawables migrated to official Material Symbols paths

### Fixed
- Quick-send uses the same send path as in-session sends, keeping state consistent
- Inner Scaffolds no longer double-consume the bottom inset
- Selection toolbar floats as a content overlay with a lighter capsule

## [v1.10.0](https://github.com/Lifky/Flikky/tree/v1.10.0) · 2026-06-26

### Added
- Favorites (codename: Ammo Box): keep messages/files as independent snapshots, favorite collections (grouping), a dedicated favorites tab, long-press favorite action on messages

### Fixed
- Message id counter is seeded from the persisted maximum on startup, preventing id collisions after import

## [v1.9.1](https://github.com/Lifky/Flikky/tree/v1.9.1) · 2026-06-24

### Changed
- Settings dialog and list polish: full-row taps select radio options, sliders on their own line, removed duplicate icons

## [v1.9.0](https://github.com/Lifky/Flikky/tree/v1.9.0) · 2026-06-24

### Added
- Session groups: group chip row on home, a unified manage dialog, batch move-to-group, date buckets within a group
- New sessions are tagged with the active group; deleting a group unbinds its sessions (undoable)

### Changed
- Multi-select actions moved to a floating selection toolbar

## [v1.8.0](https://github.com/Lifky/Flikky/tree/v1.8.0) · 2026-06-23

### Added
- Home sort/group chip row with grouped rendering; preferences persisted
- Complete MD3 type scale (with CJK paragraph line breaks) and a spacing/sizes token system

### Changed
- Settings switched to the M3 segmented list style, regrouped into six logical sections, with a title bar and per-row leading icons
- Content width capped and centered on wide screens for home/settings/transfer/history/export
- Home search bar expand animation and inset polish

## [v1.7.0](https://github.com/Lifky/Flikky/tree/v1.7.0) · 2026-06-18

### Added
- In-place home search (SearchBar): session-name and message results in groups, with jump-to-message; the separate search screen retired
- Long-press multi-select: tri-state select-all, adaptive action bar, batch pin/delete/rename

### Fixed
- Search debouncing unified onto a single query source, eliminating the "no match" flash
- Edge-to-edge fullscreen search with system bar color alignment

## [v1.6.0](https://github.com/Lifky/Flikky/tree/v1.6.0) · 2026-06-16

### Added
- Context-adaptive transfer header: the connection card collapses to a slim bar once a client connects
- Unified four-corner bubble radius customization (applies on both ends)
- Avatar grouping mode setting (first-in-group default / last / each)
- Floating message toolbar (floating/inline switchable in Settings); long-press text selection
- Waiting-for-connection loading indicator

### Changed
- Single-row inline input with the stop button moved to the header; attach sheet redesigned as two square cards
- Session background dropped gradients in favor of theme-derived solid presets plus a custom hue slider
- URL and copy button stacked vertically

### Fixed
- IME inset handling: the input bar sits directly above the keyboard with no gap
- Message input disabled until a client is connected
- Back is guarded during an active session and the settings entry is locked

## [v1.5.0](https://github.com/Lifky/Flikky/tree/v1.5.0) · 2026-06-08

### Added
- Bottom navigation architecture; settings screen: theme / avatar / session background / history retention
- 4 warm preset themes, AMOLED pure black, dark mode — instant switching via DataStore
- Avatars on both ends: set the phone avatar in Settings; the browser picks one and syncs it via client_hello
- Long-press message action bar with staggered entry animation
- New adaptive launcher icon

### Changed
- Emoji in home/search/history replaced with Material icons
- Undoing a delete restores the message to its original position

### Fixed
- Lowering the history retention limit sweeps immediately; over-limit sessions no longer flash
- Recalling your own message was blocked by a redundant senderId check
- Snackbars float above the input bar instead of blocking controls

## [v1.4.0](https://github.com/Lifky/Flikky/tree/v1.4.0) · 2026-06-04

### Added
- Import ZIP archives back into the app
- Phone-to-browser file push is now async with receive progress in the browser
- Exports use a shared JSON schema with relativePath dedup

### Fixed
- Interrupted-upload cleanup and XHR abort on disconnect
- Failed transfers show a FAILED label (the countdown auto-removal approach was dropped)
- Attach button disabled while the client is disconnected

## [v1.3](https://github.com/Lifky/Flikky/tree/v1.3) · 2026-05-24

### Added
- Message recall: long-press entry and placeholder styles on both ends, hard delete with senderId authorization, confirmation dialogs on both sides
- Full-text message search: FTS4 with a LIKE fallback; results jump to and highlight the target message
- App-layer heartbeat switched to ping/pong

### Fixed
- Crash from FTS tokenizer arguments unsupported on device
- Export WebSocket stops correctly once the download starts; reconnect stops with a prompt on `server_stopped`
- Downloads keep the original filename (Content-Disposition)
- fileCount accounting and instant-disconnect detection

## [v1.2](https://github.com/Lifky/Flikky/tree/v1.2) · 2026-05-13

### Added
- Multi-session export: multi-select on home → download a multi-session ZIP from the browser (streamed, with messages.txt / messages.json)
- Automatic service rebind on Wi-Fi changes: the server restarts on IP change with a refreshed notification, and the browser reconnects automatically
- Browser upload progress bubble; mdui snackbar replaces native alert()

### Fixed
- A series of connection-robustness issues: app-layer heartbeat detecting dead sockets, reconnect-storm suppression, senderId dedup, distinct disconnect causes (server stop vs. network loss), same-IP recovery correctly rebuilding the listening socket
- Broadcasts target the current wsHub after a rebind (the closure-captured stale reference bug, now codified as a project convention)
- `startForeground` is called on every `onStartCommand` path

## [v1.1](https://github.com/Lifky/Flikky/tree/v1.1) · 2026-04-21

### Added
- Session history: Room archival, home session list, long-press rename/pin/delete, history detail screen
- Crash recovery (orphan session finalization), empty-session rollback, FIFO retention of the latest 20 non-pinned sessions
- Selectable text in message bubbles

### Fixed
- Raised the multipart formFieldLimit, removing the 50 MiB upload cap
- Crash on file tap and vanished browser-upload-only sessions
- HomeViewModel reflective construction failure

### Other
- Open-source preparation: LICENSE, bilingual README, local notes untracked

## [v1.0](https://github.com/Lifky/Flikky/tree/v1.0) · 2026-04-18

First release — a complete LAN transfer loop between an Android phone and a browser:

- Embedded Ktor (CIO) server bound to the Wi-Fi IPv4 address only, never `0.0.0.0`; CSP and hardening headers attached to every response
- Single-use six-digit PIN authentication: consumed on success, lockout/termination on repeated failures; token carried in an HTTP-Only cookie
- Two-way text and file transfer (multipart upload / chunked download) with real-time WebSocket messaging and a 1 Hz status broadcast (uptime/files/rate)
- Browser client: mdui (MD3 Web Components) bundled offline (~380 KB), segmented PIN login page plus chat page
- Foreground service (dataSync) with notifications; the PIN is hidden on the lock screen, directing the user into the app
- Browser-uploaded files can be opened on the phone via FileProvider
