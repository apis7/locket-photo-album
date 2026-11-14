package com.locketphoto.gallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

/** Photos and videos another app asked us to open ("Open with Locket"). */
object ExternalMedia {
    private val exifDate get() = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)

    /** The MediaStore id behind [uri], if it is a MediaStore uri. */
    fun mediaStoreId(uri: Uri): Long? =
        if (uri.authority == MediaStore.AUTHORITY) runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it >= 0 } else null

    /** Finds the opened file in the library, so the viewer can page through the rest. */
    fun indexIn(items: List<MediaItem>, uri: Uri): Int {
        val id = mediaStoreId(uri)
        return items.indexOfFirst { if (id != null) it.id == id else it.uri == uri }
    }

    /** Builds a stand-alone item for a file that isn't in the library (or when we can't read the library). */
    fun describe(context: Context, uri: Uri, typeHint: String?): MediaItem? = runCatching {
        val resolver = context.contentResolver
        val mime = typeHint?.takeIf { '/' in it && !it.endsWith("/*") } ?: resolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/") || (mime.isBlank() && typeHint?.startsWith("video/") == true)
        var name = uri.lastPathSegment.orEmpty()
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        var w = 0
        var h = 0
        var rotation = 0
        var duration = 0L
        var taken: Long? = null
        if (isVideo) {
            MediaMetadataRetriever().apply {
                try {
                    setDataSource(context, uri)
                    w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    release()
                }
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts); true } ?: return null
            w = opts.outWidth
            h = opts.outHeight
            runCatching {
                resolver.openInputStream(uri)?.use {
                    val exif = ExifInterface(it)
                    rotation = exif.rotationDegrees
                    taken = (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME))
                        ?.let { t -> runCatching { exifDate.parse(t)?.time }.getOrNull() }
                }
            }
        }
        val now = System.currentTimeMillis()
        MediaItem(
            id = mediaStoreId(uri) ?: -(uri.hashCode().toLong() and 0xffffffffL) - 1,
            uri = uri,
            isVideo = isVideo,
            mimeType = mime.ifBlank { if (isVideo) "video/*" else "image/*" },
            takenAt = taken ?: now,
            modifiedAt = now,
            durationMs = duration,
            width = w.coerceAtLeast(0),
            height = h.coerceAtLeast(0),
            orientation = rotation,
            bucket = null,
            isFavorite = false,
            name = name,
        )
    }.getOrNull()
}
