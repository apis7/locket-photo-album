package com.locketphoto.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Bundle
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
    fun observeAll(): Flow<List<MediaItem>> = observe { query(trashed = false) }

    /** Emits what's in the trash (system keeps it 30 days), newest-trashed first. */
    fun observeTrash(): Flow<List<MediaItem>> = observe { query(trashed = true).sortedByDescending { it.expiresAt } }

    @OptIn(FlowPreview::class)
    private fun observe(load: () -> List<MediaItem>): Flow<List<MediaItem>> =
        changes()
            .onStart { emit(Unit) }
            .debounce(250)
            .conflate()
            .map { load() }
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

    private fun query(trashed: Boolean): List<MediaItem> {
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
            FileColumns.ORIENTATION,
            FileColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${FileColumns.MEDIA_TYPE} IN (?, ?)")
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(FileColumns.MEDIA_TYPE_IMAGE.toString(), FileColumns.MEDIA_TYPE_VIDEO.toString()),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${FileColumns.DATE_TAKEN} DESC")
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        val out = ArrayList<MediaItem>(if (trashed) 64 else 4096)
        resolver.query(filesUri, projection, args, null)?.use { c ->
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
            val iOri = c.getColumnIndexOrThrow(FileColumns.ORIENTATION)
            val iExp = c.getColumnIndexOrThrow(FileColumns.DATE_EXPIRES)
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
                    orientation = c.getInt(iOri),
                    bucket = c.getString(iBucket),
                    isFavorite = c.getInt(iFav) == 1,
                    expiresAt = c.getLong(iExp) * 1000,
                )
            }
        }
        // DATE_TAKEN can be missing; re-sort on the resolved time (stable, already mostly ordered).
        out.sortByDescending { it.takenAt }
        return out
    }
}
