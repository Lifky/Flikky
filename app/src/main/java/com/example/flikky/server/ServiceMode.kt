package com.example.flikky.server

/**
 * v1.2 §2.3 — the transfer service and the export service are mutually
 * exclusive runtime modes sharing the same [KtorServer] class. The mode is
 * decided at service start; switching requires stopping and restarting.
 *
 * - [Transfer] — the v1.x behavior: auth + messages + files + WebSocket hub.
 * - [Export]   — a short-lived server that only serves the export page and
 *                streams the zip; inbound transfer routes are not mounted so
 *                the browser cannot accidentally push content into a session
 *                that is already being exported.
 */
sealed class ServiceMode {
    data object Transfer : ServiceMode()
    data object Export : ServiceMode()
}
