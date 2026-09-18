# Changelog

**English** · [简体中文](./CHANGELOG.zh-CN.md)

This file records user-facing changes for each Flikky release, loosely following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow `x.y.z`: x for major architectural changes, y for new features, z for bug fixes. Dates are tag creation dates.

## [v1.21.0](https://github.com/Lifky/Flikky/releases/tag/v1.21.0) · 2026-09-19

Three new channels and one permission model. The phone's album becomes browsable from both ends, installed apps can be extracted and sent as APKs, the connection card offers a QR code, and a single panel now states exactly what the other end is allowed to see.

### Added

#### What the other end can see
- A **peer permissions panel** in the session header, holding every "what may the browser see" switch in one place. Each channel reads as one of three states: unavailable (the phone lacks the permission), off, or on — so "the switch is on but nothing shows up" is no longer a silent dead end
- The header now **permanently states which channels are open**, next to the connected device's name
- A **channel lock where the content is**: the files and album tabs each carry a lock button in the bottom-left corner that closes that channel to the peer without leaving the screen
- **Favorites split into two independent axes**: whether the feature exists in the app, and whether the browser may see it. Turning the feature off no longer silently implies the peer had access

#### The phone's album
- An **album tab in the session screen**: a single timeline grouped by capture date, three columns, tap to preview and long-press to enter multi-select. **Drag after the long press to select a range**, dragging back undoes what that gesture added, and the edge of the screen auto-scrolls while you hold
- An **album panel in the browser**: the same timeline, with fixed-size thumbnails that reflow as the panel is resized, a per-tile selection affordance (a mouse has no long press), and a multi-select download that reuses the files panel's toolbar
- **Browse by album folder as well as by date** on both ends: a switch between the timeline and a grid of album covers built from MediaStore's bucket names, with each bucket's own timeline one tap in. A timeline answers "what did I just shoot"; a bucket answers "where is the one WeChat saved"
- The album uses **Android's narrow media permissions** rather than All files access, and supports Android 14+ "selected photos only" — the app states how many items it can see and offers a way back to the system picker to widen the scope

#### Sending an installed app
- An **apps tab in the add sheet**: search installed apps, pick one, and Flikky extracts its base APK and sends it into the session. The sent file is named `AppName_Version.apk`
- Apps with **split APKs are sent as base.apk only, with an explicit warning** — the pieces cannot be reassembled into a working install from one file
- **APK rows offer an install action** — in the files overview, in the chat bubble once a transfer completes, and in history — handing the file to the system installer
- **APK packages are their own file category** in the files overview, with the official `apk_document` symbol
- The app list shows **user-installed apps by default, with a toggle to include system apps**, and offers a route into the system permission screen when the list comes back empty

#### Reaching the address without typing
- A **QR code button on the connection card**, next to "copy address", opening a bottom sheet with the code. It encodes **only the URL** — the single-use PIN stays on the phone screen for the user to enter, because a scannable credential is a new way to lose one

### Changed
- The two peer gates moved out of quick settings into the permissions panel; quick settings keeps only what changes the app's own appearance
- "Send an existing file" is now the second tab of the add sheet rather than a separate action on the tab row, so everything that puts content into a session starts from one button
- The session header's three panel actions moved to their own row. Sharing a row with the device name meant the name was always the part that got truncated
- The lock and selection FABs across the files and album tabs now share one geometry, declared once — the two tabs cannot drift apart
- **Language changes reach the browser immediately.** The browser used to poll once a second for a setting that changes a few times a year; it now follows the app's own configuration callback, and polls only as a fallback when a connection is idle
- Album thumbnails are fetched once and reused across virtual rows, with a concurrency ceiling, request de-duplication, and an in-page cache that is cleared on disconnect
- The app's home and favorites empty states now share one illustration anchor, so the two pages line up
- **The server's address check tightened from a blacklist to an allowlist.** It previously excluded a few known-bad prefixes and accepted everything else, which would have let a public IPv4 through; it now accepts only RFC1918 private ranges and rejects VPN and cellular transports outright

### Fixed

- **Closing a peer channel now stops a transfer already in flight.** The gate was checked once per request, so a 2 GB download would run to completion after the user closed access — and closing it is usually motivated by exactly that file not reaching the peer. Both the storage and album streams now re-read the gate before every 64 KB block
- **The phone and the browser disagreed about which day a photo was taken.** Both ends derived the date from the timestamp using their own device's timezone, so when the phone and the computer sat in different zones, photos taken near midnight fell into different groups on each end — different group counts, different labels, the same library looking like two libraries. The capture date is now computed once on the phone and sent with each item
- Photos dated in the future by a mis-set camera clock formed several separate groups all labelled "Today"
- Browser album thumbnails scaled with the panel width and always sat three to a row; they are now a fixed size that reflows
- Scrolling the browser album was jerky and the mouse wheel often did nothing: the virtual window rebuilt itself on every scroll event, which also re-requested thumbnails already on screen and fought the browser's scroll anchoring. The window now only adds and removes the rows that changed
- A thumbnail that failed to load was retried on every re-render — one missing item produced hundreds of requests. Failures are now remembered until an explicit refresh
- Dragging a thumbnail in the browser album triggered the chat's "drop to send" overlay, offering to send the phone's own photo back to the phone
- The album listed records that are not browsable photos: files still being written, items in the trash, and zero-byte rows
- **A Wi-Fi change could leave the service bound but unreachable.** Binding failures are no longer remembered as successes, the same address can be retried, hotspot changes are picked up without a system callback, and a stale callback can no longer revive a stopped service
- After moving to a new IP the app now issues a fresh single-use PIN and invalidates the old cookie, because a cookie cannot cross hosts. Same-IP rebinds keep the existing login
- The browser could act on events from a socket it had already replaced, and a reconnect only appended new history — file completions, failures and recalls that happened while disconnected are now reconciled
- The browser's language poller kept running after the app disconnected
- Opening the floating action bar in History or the session screen could loop redrawing itself

## [v1.20.0](https://github.com/Lifky/Flikky/releases/tag/v1.20.0) · 2026-09-13

The largest release so far, built in three stages: the phone's own storage becomes browsable from both ends, every file surface gains sorting and search, thumbnails and preview reach the storage and favorites lists, and the leading visual is customizable throughout.

### Added

#### Browsing the phone's storage
- A **files tab in the session screen**: browse the phone's own storage, tap files to select them (selection accumulates across directories), and send the whole selection into the session in one go. The tab sits next to the chat tab and swipes between them
- A **files panel in the browser**: browse the phone's storage remotely, walk into directories through a collapsing breadcrumb, and download files one at a time or as a multi-select batch. Sending from the phone is not required
- A new **"let the computer browse phone storage" switch, off on a fresh install**. While it is off the browser shows no files destination at all, and every storage endpoint answers `404`. Listing files needs Android's "All files access" permission, which has no read-only variant — Flikky only ever reads. See the security section of the README
- A **show hidden files** setting, off by default
- The server now **binds the hotspot address when the phone itself is the access point**. This case was in the stated security model from the start but had never worked: with no Wi-Fi network connected the app could not find an address and the service refused to start

#### Sorting and search
- **Sorting on six surfaces**: the home screen, favorites, the files overview, the files tab on the phone, and the files and favorites panels in the browser. Tapping a new key uses that key's natural direction — name ascending, time and size descending — and tapping the current key flips it. Every surface remembers its own choice, and the phone and the browser remember separately
- What "time" means is stated per surface: session start on the home screen, the moment it was saved in favorites, message time in the files overview, and filesystem modification time for storage files
- **Search within the current folder** on both ends: filtering happens as you type with no new request, and matches folders as well as files. The keyword clears when you change directory
- The home screen's **sectioning choice** — none, by state, or by date. All three modes had been built but were never connected to anything, so none of them had ever rendered on a device

#### Thumbnails and preview
- Images and videos in the storage list and the favorites panel now show **thumbnails on both ends**, served from a disk cache keyed by path, modification time and size, and evicted least-recently-used under a ceiling
- Tapping a thumbnail **opens a preview**: the in-app viewer with pinch-to-zoom for images on the phone, the system player for video, and one shared fullscreen lightbox for all three sources in the browser. Tapping the row itself still selects, exactly as before
- A **thumbnail cache setting**: a ceiling of 0 (no caching), 50, 100 or 200 MB, the space currently used, and a button to clear it. "Delete all data" clears it too
- SVG files are deliberately excluded from both thumbnails and inline rendering

#### Customizable leading visual
- **The shape of the leading container can be chosen** from a grid of 25 official Material 3 Expressive shapes, and applies everywhere a file row has one: the files overview, favorites, both quick-send sheets, the files tab, the chat file bubble, and the three matching surfaces in the browser
- **Three colour modes for the leading container**: one theme colour for every type (as before), colours harmonized with the theme, or fixed colours. Each mode shows a six-colour preview strip matching the six file categories
- **Archives are their own file category**, with the official `folder_zip` symbol — ZIP, RAR and 7z no longer fall in with "other". The category filter now carries seven chips and scrolls horizontally on a narrow screen
- Both choices sync to the browser live, survive a restart, and travel in an export/import

### Changed
- Opening a large folder now fills in progressively on both ends. The server streams the listing as NDJSON, flushed per entry, and each end appends rows as they arrive instead of waiting for the whole directory. Reading each entry's attributes in one call rather than three cuts a 2000-entry folder from roughly 6000 filesystem calls to 2000
- The browser's file list renders only the rows near the viewport. A 10,000-entry folder used to put 110,000 elements in the page; it now holds a few hundred, which is where the memory use and the scrolling stutter came from
- On the phone, listing runs off the main thread, the path and a progress indicator appear the instant you tap, and a new navigation cancels the previous one
- Going back to a folder you have already opened is instant and lands where you left off, on both the phone and the browser. There is deliberately no automatic re-read; a refresh button in each panel is the manual way
- Both ends say how much of the selection sits in other folders, so a count that exceeds the ticks on screen is no longer a mystery
- Select all and deselect are one two-state button in the browser's panel head, and it hides when the folder holds nothing selectable. It stays inert until the listing is complete, because "all" has no defined meaning while rows are still arriving
- Both file lists now end with a marker — "Loading… N so far" while entries arrive, "N items" once done — so it is possible to tell a finished list from one that merely stopped growing
- A directory reads as an item count on both ends, instead of "Folder" on the phone and "13 items" in the browser
- The app's selection toolbar is now a Material 3 FAB menu
- The session screen shows its tab row only once a browser is connected — everything the files tab can do needs a connection
- File rows fade in one by one with a capped stagger, the list slides in the direction of travel when you enter or leave a folder, and favourite rows share the same entrance, so the two panels read as one system
- The loading bar collapses upward on a spring when a listing finishes, so the list glides up instead of jumping. Both ends do this now
- The browser's lightbox eases open: the scrim fades, the image settles in from slightly smaller, and the swap from thumbnail to full image cross-fades instead of cutting. The chat lightbox gained this too, since all three sources share one implementation. "Reduce motion" turns all of it off
- Icons throughout the browser are now hidden from screen readers. A destination used to be read out as "star Favorites", because the icon glyph is generated content carrying an internal identifier
- The browser's navigation reports the current destination with `aria-current` instead of `aria-selected`. The latter is ignored on a plain button inside a `<nav>`, so which destination you were on was never announced at all
- Under the single-colour mode, the leading container on the phone moved from the primary tones to the secondary tones — which is what the browser already used. One token now, on both ends
- Storage browsing uses the plain folder symbol rather than `folder_shared`, and the show-hidden-files row uses Folder Eye
- Six separate byte-formatting implementations in the Kotlin source were collapsed into one

### Fixed

- Collapsing a settings row left a double gap behind it, and the spring overshoot made the row bounce on the way in
- A segmented control's indices shifted while the row above it was collapsing
- Nested scaffolds reapplied the window insets, so the navigation bar was padded twice
- `hidden` did not actually hide in the browser: an author `display` rule outranks the browser's own `[hidden]` rule, so an element hidden that way was still drawn. This was a global defect, and is fixed globally
- Selecting a favourite rebuilt the whole favorites list
- A further 31 defects in this release's own new code — storage browsing, sorting, thumbnails — were found and fixed before it shipped, so no released build ever carried them

## [v1.19.0](https://github.com/Lifky/Flikky/releases/tag/v1.19.0) · 2026-08-26

### Added
- The browser client was rebuilt in Material 3 Expressive: a navigation rail, the chat pane and a function pane sit side by side, and the handle between the two panes resizes them by drag or keyboard, with the ratio and the collapsed state surviving a refresh. A narrow window swaps the rail for a bottom navigation bar. The login and export pages were rebuilt to match
- A favorites panel in the browser: search, category filters, and multi-select batch saving. Read-only — adding, editing and deleting still live on the phone
- A settings panel in the browser: layout preferences (rail on the right, swap the two panes, reset the split), read-only rows mirroring the phone (language, theme, session timestamps), and an About section showing the version
- In-session quick settings now cover **every setting that syncs to the browser**: language, theme colour, dark mode, AMOLED, device name, both avatars, bubble corners, avatar display, chat background, session timestamps, message action style, recall, allow peer recall, and favorites. The settings tab is locked while a session runs, so anything missing here could not be changed for the whole session
- The browser follows the phone's AMOLED switch
- Row actions for finished sessions are complete, so acting on a single session no longer requires multi-select

### Changed
- The default theme colour is now Anan blue
- The browser is now "nothing selectable by default, content opts in": message text, file names, favorite entries and the version string select and copy normally, while interface labels and icons no longer get picked up by accident
- Icon glyphs are generated by CSS instead of being page text — a long press in a mobile browser no longer selects internal icon names like `arrow_upward`, and copying a whole message no longer drags the icon name along
- Scrollbars appear only while scrolling instead of permanently holding space
- The favorites category filter now uses mdui's own chip component
- The login and export buttons no longer morph their corners on press; only the light/dark feedback remains

### Fixed
- **Two-way sync: changing a setting *back to its default* had no effect.** Session timestamps could not be turned off, message action style could not be switched back to inline buttons, re-enabling peer recall did nothing, and favorites could not be turned off — each of them needed a manual browser refresh. Fields sitting at their default value were omitted entirely from the data pushed to the browser, which read an absent field as "unchanged". Every field is now always sent
- After switching the phone between transfer and export, a browser left on the old address would reconnect endlessly (or hit a 404) on refresh; it now redirects to the right page
- Long-pressing an icon in either the browser or the app selected and copied the icon's name
- Files sent *by* the browser were wrongly counted into the "Download as ZIP" save set
- The logo did not adapt to dark mode, and a later change stopped it rendering at all
- The save-all floating button did not match the input dock's height, and the dock's width jumped when it appeared or hid
- In the quick settings' session-behaviour group, the last row's bottom corners did not match the first row's top corners
- Wording in the group dialog and several browser labels

## [v1.18.0](https://github.com/Lifky/Flikky/releases/tag/v1.18.0) · 2026-08-15

### Added
- Session timestamp dividers on both ends (off by default): a centered pill shows `yy/MM/dd HH:mm` before the first message and again whenever a message arrives 5+ minutes after the last shown divider. One switch in the app controls both ends; the browser follows instantly, including in already-rendered history
- Keep screen on during a session (off by default): while the service is running and the session screen is in front, the display no longer times out; leaving the screen or stopping the service restores normal behavior
- Save-all button in the browser: once 2 or more received files are ready, a floating button offers "Save each (N)" — downloading every file one by one under its original name — and "Download as ZIP", a streamed plain-files archive with duplicate names auto-renamed. The ZIP endpoint uses the same session authentication as every other file route

### Changed
- The two new setting rows carry official Material Symbols leading icons, with the timestamp explanation moved into an info dialog

### Fixed
- Dragging the bubble corner radius back to its default no longer makes browser bubbles fall back to the old default; both ends now agree the default is 10

## [v1.17.1](https://github.com/Lifky/Flikky/releases/tag/v1.17.1) · 2026-08-13

### Added
- Row overflow menu on favorites: share, move to collection, open with, save to gallery (media only), save as, and delete. Text favorites get their own set — copy, move, delete — since they have no file on disk
- The favorite row's trailing control is now a split button: the left half sends, the right half opens that menu
- Favorites selection toolbar gained the full batch set (share, save to gallery, save as) alongside move and delete. Each file action runs on the subset it can touch and reports what it skipped
- Row overflow menu on ended sessions on the home screen: pin, rename, move to group, export, delete — no more long-pressing into multi-select to act on one session
- Starting a transfer without a usable Wi-Fi address is now refused with a dialog that explains any device on the same network can connect from its browser, and a button that opens Wi-Fi settings

### Changed
- Favorite rows follow the files-overview interactions: tap an image to preview it in the app, tap another file to hand it to an external app, tap a text favorite to copy it, and tap the leading visual to enter multi-select
- Non-media file rows draw their category icon inside a 40dp Material Expressive cookie container, so thumbnails and icons occupy the same footprint and every headline starts on the same line. All four file-row surfaces share one implementation
- Text favorites take that same leading slot with a quote symbol, on the favorites screen and in the quick-send sheet
- The favorites screen calls its containers "collections" everywhere; the shared group chips, move sheet and manage dialog take their wording from the caller
- Audio files use the official audio_file symbol
- A selected row's leading container turns a light surface tone instead of a solid primary fill

### Fixed
- Media thumbnails and category icons no longer sit at different widths, which used to shift the headline left and right between rows
- Leading content stays vertically centered when a favorite headline wraps to two lines
- Text favorites in the quick-send sheet were missing their leading entirely, indenting their titles differently from file rows
- A thumbnail that fails to decode falls back to the icon container instead of stretching a 24dp vector to 40dp
- Starting without Wi-Fi no longer leaves the serving screen showing an empty URL, an empty PIN and a climbing uptime for a service that already killed itself
- The favorite row's split button is smaller, and its menu half is narrower than its send half instead of both being the same width

## [v1.17.0](https://github.com/Lifky/Flikky/releases/tag/v1.17.0) · 2026-08-10

### Added
- Media thumbnails in chat bubbles on both ends: images and videos render as proportionally scaled thumbnails; tapping opens an in-app image preview on Android and a fullscreen lightbox in the browser. Favorite file rows show thumbnails too
- Browser message actions now follow the app's "message action style" setting: a persistent inline action bar, or a floating hover toolbar with right-click / long-press context menu; the whole bubble acts as the download target under the floating style
- "Allow recalling peer messages" setting: when enabled, either end can recall the other end's messages; enforced by the server on both ends
- Files overview action rework: tap a row to open or preview, tap the thumbnail to enter multi-select, per-row overflow menu, and a batch toolbar with favorite, share, save to gallery, save as, and delete
- Check for updates: a manual entry under Settings > About, plus an optional auto-check on launch (off by default). The check contacts only the GitHub releases API and sends no device or account data
- Browser avatar: the avatar entry in Settings now has App and Browser tabs; the browser avatar is persisted on the phone and pushed to the browser when it connects
- Custom theme color: pick any seed color in an interactive dialog (saturation/value panel, hue slider, two-way hex input); generates a full Material 3 light/dark/contrast palette locally and syncs to the browser
- Quick-send sheet in an active session can now pick from all stored files, not just favorites
- Share a single selected favorite file; "delete all data" action in Settings

### Changed
- New-install defaults: bubble corner radius 10, message recall on, allow peer recall on, inline message actions, and "return during session" on — existing explicit choices are never overwritten
- Browser disconnected state is a non-closable blurred dialog instead of a snackbar; the connection watermark stays centered in the message viewport instead of scrolling with messages
- Multi-select top bars use a select-all/deselect icon toggle
- SVG files are classified as "Other" on both ends: no preview, lightbox, thumbnail, or save-to-gallery, since Android does not treat SVG as media
- File bubbles on both ends use per-category icons (image/video/audio/document/other), matching the files overview list
- Long setting summaries moved into info dialogs; action icons realigned with their semantics (preview uses the visibility icon)

### Fixed
- Message lists stay anchored while the keyboard resizes the screen
- Media bubbles size themselves from real media dimensions; browser media bubbles are frameless with the image as the sole width source
- Each file in a multi-file browser upload is processed independently, so one failure no longer aborts the rest
- Video thumbnails decode correctly for stored files without an extension
- Favorite actions in the files overview are hidden when the favorites feature is disabled
- The "check for updates" row keeps a stable height while checking
- Browser file bubbles show their icon during live transfers, and message actions are restored after a phone-to-browser file transfer
- Importing an archive from Settings routes through the same conflict decision flow as other imports

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
