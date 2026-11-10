package com.locketphoto.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class MediaRepository(private val resolver: ContentResolver) {

    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Emits the whole library now, and again whenever MediaStore changes. */
    @OptIn(FlowPreview::class)
    fun observeAll(): Flow<List<MediaItem>> =
        changes()
            .onStart { emit(Unit) }
            .debounce(250)
            .conflate()
            .map { queryAll() }
            .flowOn(Dispatchers.IO)

    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(filesUri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun queryAll(): List<MediaItem> {
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.MIME_TYPE,
            FileColumns.DATE_TAKEN,
            FileColumns.DATE_ADDED,
            FileColumns.DATE_MODIFIED,
            FileColumns.DURATION,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns.IS_FAVORITE,
        )
        val selection = "${FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            FileColumns.MEDIA_TYPE_IMAGE.toString(),
            FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val out = ArrayList<MediaItem>(4096)
        resolver.query(filesUri, projection, selection, args, "${FileColumns.DATE_TAKEN} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val iMime = c.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
            val iTaken = c.getColumnIndexOrThrow(FileColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(FileColumns.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val iDur = c.getColumnIndexOrThrow(FileColumns.DURATION)
            val iW = c.getColumnIndexOrThrow(FileColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(FileColumns.HEIGHT)
            val iBucket = c.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)
            val iFav = c.getColumnIndexOrThrow(FileColumns.IS_FAVORITE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val isVideo = c.getInt(iType) == FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val modified = c.getLong(iMod) * 1000
                val taken = c.getLong(iTaken).takeIf { it > 0 }
                    ?: (c.getLong(iAdded) * 1000).takeIf { it > 0 }
                    ?: modified
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = isVideo,
                    mimeType = c.getString(iMime).orEmpty(),
                    takenAt = taken,
                    modifiedAt = modified,
                    durationMs = c.getLong(iDur),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    bucket = c.getString(iBucket),
                    isFavorite = c.getInt(iFav) == 1,
                )
            }
        }
        // DATE_TAKEN can be missing; re-sort on the resolved time (stable, already mostly ordered).
        out.sortByDescending { it.takenAt }
        return out
    }
}
