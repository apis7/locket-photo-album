package com.locketphoto.gallery.thumbs

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * A grid thumbnail request. Uses the system's own thumbnail cache
 * (ContentResolver.loadThumbnail) — fast, works for photos and videos alike,
 * and costs no extra storage.
 */
data class Thumb(val uri: Uri, val sizePx: Int, val version: Long)

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String =
        "thumb:${data.uri}:${data.sizePx}:${data.version}"
}

class ThumbFetcher(
    private val data: Thumb,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val signal = CancellationSignal()
        val bitmap = try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { signal.cancel() }
                cont.resumeWith(runCatching {
                    resolver.loadThumbnail(data.uri, Size(data.sizePx, data.sizePx), signal)
                })
            }
        } finally {
            signal.cancel()
        }
        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, resolver)
    }
}
