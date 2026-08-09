package com.example.flikky.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Latest-release facts the UI needs; [body] is displayed as plain text only. */
data class UpdateInfo(
    val tagName: String,
    val body: String,
    val htmlUrl: String,
)

/**
 * The only runtime network endpoint allowed by the project security rules.
 * It is called only after a manual request or explicit auto-check opt-in, carries no identifiers,
 * and never retries.
 */
private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Lifky/Flikky/releases/latest"
private const val TIMEOUT_MS = 8_000

internal suspend fun fetchLatestReleaseJson(): String = withContext(Dispatchers.IO) {
    val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

class UpdateChecker(
    private val fetchJson: suspend () -> String = ::fetchLatestReleaseJson,
) {
    /** Null means the check failed; manual callers show feedback while auto callers stay silent. */
    suspend fun check(): UpdateInfo? {
        val json = try {
            fetchJson()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        return parseLatestRelease(json)
    }

    companion object {
        fun parseLatestRelease(json: String): UpdateInfo? = try {
            val obj = Json.parseToJsonElement(json).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content
            val url = obj["html_url"]?.jsonPrimitive?.content
            if (tag.isNullOrBlank() || url.isNullOrBlank()) {
                null
            } else {
                UpdateInfo(
                    tagName = tag,
                    body = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                    htmlUrl = url,
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
