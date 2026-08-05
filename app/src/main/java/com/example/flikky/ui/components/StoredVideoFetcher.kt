package com.example.flikky.ui.components

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * Coil model for stored video blobs. Session and favorite files are persisted without a
 * file extension, so Coil cannot infer a video mime type and VideoFrameDecoder never
 * engages; wrapping the file in [StoredVideo] routes it through [StoredVideoFetcher],
 * which stamps a video/* mime so frame extraction works on extensionless files.
 */
data class StoredVideo(val file: File)

class StoredVideoFetcher(
    private val data: StoredVideo,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = SourceFetchResult(
        source = ImageSource(data.file.toOkioPath(), options.fileSystem),
        mimeType = "video/*",
        dataSource = DataSource.DISK,
    )

    class Factory : Fetcher.Factory<StoredVideo> {
        override fun create(
            data: StoredVideo,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = StoredVideoFetcher(data, options)
    }
}

/** Without an explicit key the custom model would never hit Coil's memory cache. */
class StoredVideoKeyer : Keyer<StoredVideo> {
    override fun key(data: StoredVideo, options: Options): String =
        "${data.file.path}:${data.file.lastModified()}"
}
