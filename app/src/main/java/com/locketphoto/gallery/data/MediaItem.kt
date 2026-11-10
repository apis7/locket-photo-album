package com.locketphoto.gallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String,
    /** Best-known capture time, epoch millis. */
    val takenAt: Long,
    val modifiedAt: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bucket: String?,
    val isFavorite: Boolean,
)
